package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.ChunkInfo;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.ChunkInfoRepository;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final String FILE_MD5 = "0123456789abcdef0123456789abcdef";

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private DocumentVectorRepository documentVectorRepository;

    @Mock
    private ChunkInfoRepository chunkInfoRepository;

    @Mock
    private MinioClient minioClient;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ElasticsearchService elasticsearchService;

    @Mock
    private OrgTagCacheService orgTagCacheService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void deleteDocumentRemovesOwnerDocumentFromAllStores() throws Exception {
        FileUpload upload = fileUpload("owner", "water-report.pdf");
        when(fileUploadRepository.findByFileMd5(FILE_MD5)).thenReturn(Optional.of(upload));
        when(chunkInfoRepository.findByFileMd5OrderByChunkIndexAsc(FILE_MD5))
                .thenReturn(List.of(
                        chunk("chunks/" + FILE_MD5 + "/0"),
                        chunk(" "),
                        chunk("chunks/" + FILE_MD5 + "/1")
                ));

        documentService.deleteDocument(FILE_MD5, "owner", "USER");

        verify(elasticsearchService).deleteByFileMd5(FILE_MD5);

        ArgumentCaptor<RemoveObjectArgs> removeArgs = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient, times(3)).removeObject(removeArgs.capture());
        assertThat(removeArgs.getAllValues())
                .extracting(RemoveObjectArgs::object)
                .containsExactly(
                        "merged/water-report.pdf",
                        "chunks/" + FILE_MD5 + "/0",
                        "chunks/" + FILE_MD5 + "/1"
                );

        verify(redisTemplate).delete("upload:owner:" + FILE_MD5);
        verify(documentVectorRepository).deleteByFileMd5(FILE_MD5);
        verify(chunkInfoRepository).deleteByFileMd5(FILE_MD5);
        verify(fileUploadRepository).deleteByFileMd5(FILE_MD5);
    }

    @Test
    void deleteDocumentAllowsAdminToDeleteAnotherUsersDocument() throws Exception {
        FileUpload upload = fileUpload("owner", "shared.pdf");
        when(fileUploadRepository.findByFileMd5(FILE_MD5)).thenReturn(Optional.of(upload));
        when(chunkInfoRepository.findByFileMd5OrderByChunkIndexAsc(FILE_MD5)).thenReturn(List.of());

        documentService.deleteDocument(FILE_MD5, "admin", "ADMIN");

        verify(elasticsearchService).deleteByFileMd5(FILE_MD5);
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(redisTemplate).delete("upload:owner:" + FILE_MD5);
        verify(documentVectorRepository).deleteByFileMd5(FILE_MD5);
        verify(chunkInfoRepository).deleteByFileMd5(FILE_MD5);
        verify(fileUploadRepository).deleteByFileMd5(FILE_MD5);
    }

    @Test
    void deleteDocumentRejectsNonOwnerUser() throws Exception {
        when(fileUploadRepository.findByFileMd5(FILE_MD5))
                .thenReturn(Optional.of(fileUpload("owner", "water-report.pdf")));

        assertThatThrownBy(() -> documentService.deleteDocument(FILE_MD5, "other-user", "USER"))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo("没有权限删除此文档");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        verifyNoInteractions(elasticsearchService, documentVectorRepository, chunkInfoRepository, redisTemplate);
        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        verify(fileUploadRepository, never()).deleteByFileMd5(FILE_MD5);
    }

    @Test
    void deleteDocumentRejectsMissingDocument() throws Exception {
        when(fileUploadRepository.findByFileMd5(FILE_MD5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.deleteDocument(FILE_MD5, "owner", "USER"))
                .isInstanceOfSatisfying(CustomException.class, exception -> {
                    assertThat(exception.getMessage()).isEqualTo("文档不存在");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verifyNoInteractions(elasticsearchService, documentVectorRepository, chunkInfoRepository, redisTemplate);
        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        verify(fileUploadRepository, never()).deleteByFileMd5(FILE_MD5);
    }

    private static FileUpload fileUpload(String userId, String fileName) {
        FileUpload upload = new FileUpload();
        upload.setFileMd5(FILE_MD5);
        upload.setUserId(userId);
        upload.setFileName(fileName);
        return upload;
    }

    private static ChunkInfo chunk(String storagePath) {
        ChunkInfo chunkInfo = new ChunkInfo();
        chunkInfo.setFileMd5(FILE_MD5);
        chunkInfo.setStoragePath(storagePath);
        return chunkInfo;
    }
}
