package com.studenthub.modules.electricity.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.electricity.controller.dto.ElectricityBillResponse;
import com.studenthub.modules.electricity.integration.IyunmuElectricityClient;
import com.studenthub.modules.electricity.service.ElectricityService;
import com.studenthub.modules.identity.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ElectricityServiceImpl implements ElectricityService {
    private static final Pattern SN_PATTERN = Pattern.compile("YM[0-9A-Z]{9,12}");

    private final IyunmuElectricityClient client;

    @Override
    public ElectricityBillResponse query(AuthenticatedUser user, String sn) {
        String normalized = sn == null ? "" : sn.trim().toUpperCase();
        if (!SN_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "SN 格式不正确");
        }
        JsonNode data = client.fetch(normalized);
        return new ElectricityBillResponse(
                data.path("sn").asText(normalized),
                text(data, "name"),
                text(data, "payee"),
                decimal(data, "surplusValue"),
                decimal(data, "totalValue"),
                decimal(data, "unitPrice"),
                data.path("switchStatus").isNumber() ? data.path("switchStatus").asInt() : null,
                text(data, "read_time"),
                data.path("pay_enable").asBoolean(false),
                text(data, "protocols"),
                text(data, "dev_name_format"));
    }

    private String text(JsonNode data, String field) {
        final JsonNode node = data.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private BigDecimal decimal(JsonNode data, String field) {
        final JsonNode node = data.path(field);
        if (node.isMissingNode() || node.isNull()) return null;
        return node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText("0"));
    }
}
