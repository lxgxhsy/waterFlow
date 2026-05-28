package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.entity.EsDocument;
import com.yizhaoqi.smartpai.entity.TextChunk;
import com.yizhaoqi.smartpai.model.DocumentVector;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * VectorizationService 的单元测试，重点验证：
 * 1. 从 DocumentVector 构造 TextChunk 的逻辑
 * 2. metaSummary 字段是否被正确生成并传递到 EsDocument
 */
class VectorizationServiceTest {

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private ElasticsearchService elasticsearchService;

    @Mock
    private DocumentVectorRepository documentVectorRepository;

    @InjectMocks
    private VectorizationService vectorizationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void vectorize_shouldGenerateMetaSummaryAndIndexToEs() {
        String fileMd5 = "abc123def456ghi789jkl012mno345pq";
        String userId = "1";
        String orgTag = "ORG_A";

        // 准备 DocumentVector 数据
        DocumentVector v1 = new DocumentVector();
        v1.setFileMd5(fileMd5);
        v1.setChunkId(1);
        v1.setTextContent("第一块内容，用于测试 metaSummary 生成。");

        DocumentVector v2 = new DocumentVector();
        v2.setFileMd5(fileMd5);
        v2.setChunkId(2);
        v2.setTextContent("第二块内容，继续测试元数据摘要是否正确落入 ES。");

        when(documentVectorRepository.findByFileMd5(fileMd5))
                .thenReturn(List.of(v1, v2));

        // Embedding 返回两个简单向量
        when(embeddingClient.embed(anyList()))
                .thenReturn(List.of(new float[]{0.1f, 0.2f}, new float[]{0.3f, 0.4f}));

        // 捕获写入 ES 的文档
        ArgumentCaptor<List<EsDocument>> captor = ArgumentCaptor.forClass(List.class);

        vectorizationService.vectorize(fileMd5, userId, orgTag, false);

        verify(documentVectorRepository, times(1)).findByFileMd5(fileMd5);
        verify(embeddingClient, times(1)).embed(anyList());
        verify(elasticsearchService, times(1)).bulkIndex(captor.capture());

        List<EsDocument> esDocuments = captor.getValue();
        assertEquals(2, esDocuments.size());

        EsDocument d1 = esDocuments.get(0);
        EsDocument d2 = esDocuments.get(1);

        assertEquals(fileMd5, d1.getFileMd5());
        assertEquals(1, d1.getChunkId());
        assertNotNull(d1.getMetaSummary());
        assertTrue(d1.getMetaSummary().contains("file=" + fileMd5));
        assertTrue(d1.getMetaSummary().contains("chunk=1"));

        assertEquals(fileMd5, d2.getFileMd5());
        assertEquals(2, d2.getChunkId());
        assertNotNull(d2.getMetaSummary());
        assertTrue(d2.getMetaSummary().contains("chunk=2"));

        // metaSummary 中应包含内容前缀
        assertTrue(d1.getMetaSummary().contains("第一块内容"), "metaSummary 应该包含文本前缀预览");
        assertTrue(d2.getMetaSummary().contains("第二块内容"), "metaSummary 应该包含文本前缀预览");
    }
}

