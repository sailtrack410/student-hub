package com.studenthub.modules.electricity.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 包租婆管家（iyunmu）云平台电表查询客户端。
 * 该接口为只读查询，云平台未做鉴权；SN 由用户扫码获得。
 */
@Component
public class IyunmuElectricityClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;

    public IyunmuElectricityClient(
            ObjectMapper objectMapper,
            @Value("${studenthub.electricity.iyunmu-base-url:https://bzp.iyunmu.com/bzp_backup/v1/client}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** 返回云端 data 节点；失败抛出业务异常。 */
    public JsonNode fetch(String sn) {
        try {
            final String body = objectMapper.writeValueAsString(Map.of("sn", sn));
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/client_device_info/"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "电费服务暂时不可用");
            }
            final JsonNode root = objectMapper.readTree(response.body());
            if (root.path("code").asInt(0) != 200) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "未查询到该电表，请核对 SN");
            }
            final JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "未查询到该电表，请核对 SN");
            }
            return data;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "电费服务请求失败，请稍后重试");
        }
    }
}
