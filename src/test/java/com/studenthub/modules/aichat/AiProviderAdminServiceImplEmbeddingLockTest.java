package com.studenthub.modules.aichat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.aichat.controller.dto.AiDefaultModelSetRequest;
import com.studenthub.modules.aichat.entity.AiDefaultModelEntity;
import com.studenthub.modules.aichat.entity.AiModelEntity;
import com.studenthub.modules.aichat.entity.AiProviderEntity;
import com.studenthub.modules.aichat.mapper.AiDefaultModelMapper;
import com.studenthub.modules.aichat.mapper.AiModelMapper;
import com.studenthub.modules.aichat.mapper.AiProviderMapper;
import com.studenthub.modules.aichat.service.AiConfigService;
import com.studenthub.modules.aichat.service.impl.AiEmbeddingModelLockService;
import com.studenthub.modules.aichat.service.impl.AiProviderAdminServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderAdminServiceImplEmbeddingLockTest {

    private static final String LOCK_MESSAGE = "知识库已有 4 个向量分块，嵌入模型不可更改";

    @Mock
    private AiProviderMapper providerMapper;
    @Mock
    private AiModelMapper modelMapper;
    @Mock
    private AiDefaultModelMapper defaultModelMapper;
    @Mock
    private AiConfigService aiConfigService;
    @Mock
    private AiEmbeddingModelLockService embeddingModelLockService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private HttpClient.Builder directHttpClientBuilder;

    @InjectMocks
    private AiProviderAdminServiceImpl service;

    @Test
    void changingLockedEmbeddingModelIsRejected() {
        when(embeddingModelLockService.current())
                .thenReturn(new AiEmbeddingModelLockService.LockState(true, "BAAI/bge-m3", 4, LOCK_MESSAGE));
        when(modelMapper.selectById(2L)).thenReturn(model(2L, "other-embedding"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.setDefault(1L, new AiDefaultModelSetRequest(2L, "EMBEDDING")));

        assertEquals(LOCK_MESSAGE, exception.getMessage());
        verify(defaultModelMapper, never()).updateById(any(AiDefaultModelEntity.class));
        verify(defaultModelMapper, never()).insert(any(AiDefaultModelEntity.class));
    }

    @Test
    void clearingLockedEmbeddingDefaultIsRejected() {
        when(embeddingModelLockService.current())
                .thenReturn(new AiEmbeddingModelLockService.LockState(true, "BAAI/bge-m3", 4, LOCK_MESSAGE));

        assertThrows(BusinessException.class,
                () -> service.setDefault(1L, new AiDefaultModelSetRequest(null, "EMBEDDING")));

        verify(defaultModelMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void deletingLockedEmbeddingModelIsRejected() {
        AiModelEntity current = model(1L, "BAAI/bge-m3");
        AiDefaultModelEntity existing = new AiDefaultModelEntity();
        existing.setId(10L);
        existing.setTypeCode("EMBEDDING");
        existing.setModelId(1L);
        when(modelMapper.selectById(1L)).thenReturn(current);
        when(embeddingModelLockService.current())
                .thenReturn(new AiEmbeddingModelLockService.LockState(true, "BAAI/bge-m3", 4, LOCK_MESSAGE));
        when(defaultModelMapper.selectOne(any())).thenReturn(existing);

        assertThrows(BusinessException.class, () -> service.deleteModel(1L, 1L));

        verify(modelMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void settingTheAlreadyLockedModelIsAllowed() {
        AiModelEntity current = model(1L, "BAAI/bge-m3");
        AiDefaultModelEntity existing = new AiDefaultModelEntity();
        existing.setId(10L);
        existing.setTypeCode("EMBEDDING");
        existing.setModelId(1L);
        AiProviderEntity provider = new AiProviderEntity();
        provider.setId(9L);
        provider.setProviderName("硅基流动");
        current.setProviderId(9L);

        when(embeddingModelLockService.current())
                .thenReturn(new AiEmbeddingModelLockService.LockState(true, "BAAI/bge-m3", 4, LOCK_MESSAGE));
        when(modelMapper.selectById(1L)).thenReturn(current);
        when(defaultModelMapper.selectOne(any())).thenReturn(existing);
        when(modelMapper.selectList(null)).thenReturn(List.of(current));
        when(defaultModelMapper.selectList(null)).thenReturn(List.of(existing));
        when(providerMapper.selectList(null)).thenReturn(List.of(provider));

        service.setDefault(1L, new AiDefaultModelSetRequest(1L, "EMBEDDING"));

        verify(defaultModelMapper).updateById(existing);
    }

    private AiModelEntity model(Long id, String providerModelId) {
        AiModelEntity model = new AiModelEntity();
        model.setId(id);
        model.setProviderModelId(providerModelId);
        model.setModelType("EMBEDDING");
        model.setEnabled(true);
        return model;
    }
}
