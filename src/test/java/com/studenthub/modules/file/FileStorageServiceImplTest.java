package com.studenthub.modules.file;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.integration.storage.BinaryStorage;
import com.studenthub.modules.file.config.FileProperties;
import com.studenthub.modules.file.entity.FileStorageEntity;
import com.studenthub.modules.file.mapper.FileStorageMapper;
import com.studenthub.modules.file.service.impl.FileStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileStorageServiceImplTest {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3, 4};

    @Mock
    private FileStorageMapper fileStorageMapper;

    @Mock
    private BinaryStorage binaryStorage;

    private FileStorageServiceImpl fileStorageService;

    @BeforeEach
    void setUp() {
        FileProperties properties = new FileProperties();
        properties.setStorageDir("target/test-files");
        properties.setSignSecret("unit-test-secret");
        fileStorageService = new FileStorageServiceImpl(fileStorageMapper, properties, binaryStorage);
        when(fileStorageMapper.insert(any(FileStorageEntity.class))).thenAnswer(invocation -> {
            FileStorageEntity entity = invocation.getArgument(0);
            entity.setId(123L);
            entity.setCreatedAt(java.time.LocalDateTime.now());
            return 1;
        });
        when(fileStorageMapper.selectById(123L)).thenAnswer(invocation -> {
            FileStorageEntity entity = new FileStorageEntity();
            entity.setId(123L);
            entity.setStorageKey("test-storage-key.png");
            entity.setContentType("image/png");
            entity.setFileSize((long) PNG_BYTES.length);
            entity.setCreatedAt(java.time.LocalDateTime.now());
            return entity;
        });
    }

    @Test
    void storeValidPngWritesBinaryAndReturnsSignedPath() {
        Long fileId = fileStorageService.store(PNG_BYTES, "photo.png", "image/png", 1002L).fileId();

        assertEquals(123L, fileId.longValue());
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(binaryStorage).put(anyString(), contentCaptor.capture(), anyString());
        assertEquals(PNG_BYTES.length, contentCaptor.getValue().length);

        String viewPath = fileStorageService.signViewPath(fileId);
        assertTrue(viewPath.startsWith("/api/common/files/view/123?expires="));
        assertTrue(viewPath.contains("sign="));
    }

    @Test
    void rejectNonImageMagicBytes() {
        byte[] fake = "GIF89a-this-is-not-allowed-at-all".getBytes();
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileStorageService.store(fake, "fake.png", "image/png", 1002L));
        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), exception.getCode());
    }

    @Test
    void rejectOversizeFile() {
        byte[] big = new byte[6 * 1024 * 1024];
        big[0] = (byte) 0x89;
        big[1] = 0x50;
        big[2] = 0x4E;
        big[3] = 0x47;
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileStorageService.store(big, "big.png", "image/png", 1002L));
        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), exception.getCode());
    }

    @Test
    void rejectDisallowedExtension() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fileStorageService.store(PNG_BYTES, "photo.gif", "image/png", 1002L));
        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), exception.getCode());
    }

    @Test
    void signatureRoundTripAndTamperDetection() {
        Long fileId = fileStorageService.store(PNG_BYTES, "photo.png", "image/png", 1002L).fileId();
        String viewPath = fileStorageService.signViewPath(fileId);
        long expires = Long.parseLong(viewPath.split("expires=")[1].split("&")[0]);
        String sign = viewPath.split("sign=")[1];

        assertNotNull(fileStorageService.requireBySignature(fileId, expires, sign));

        String wrongSign = sign.substring(0, sign.length() - 2) + "xx";
        assertThrows(BusinessException.class,
                () -> fileStorageService.requireBySignature(fileId, expires, wrongSign));
        assertThrows(BusinessException.class,
                () -> fileStorageService.requireBySignature(fileId, expires - 10_000, sign));
    }

    @Test
    void replaceImageValuesWithSignedUrls() {
        List<String> urls = fileStorageService.signViewPaths(List.of(1L, 2L));
        assertEquals(2, urls.size());
        assertTrue(urls.get(0).contains("/api/common/files/view/1?"));
    }
}
