package com.yizhaoqi.smartpai.knowledge;

import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.model.DocumentVector;
import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.service.HybridSearchService;
import com.yizhaoqi.smartpai.service.KnowledgeGraphService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 知识引擎门面：统一封装 RAG 检索、知识图谱、文档 chunk 等能力，供上层或开放 API 使用。
 */
@Service
public class KnowledgeEngineService {

    private final HybridSearchService hybridSearchService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final DocumentVectorRepository documentVectorRepository;
    private final FileUploadRepository fileUploadRepository;

    public KnowledgeEngineService(HybridSearchService hybridSearchService,
                                  KnowledgeGraphService knowledgeGraphService,
                                  DocumentVectorRepository documentVectorRepository,
                                  FileUploadRepository fileUploadRepository) {
        this.hybridSearchService = hybridSearchService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.documentVectorRepository = documentVectorRepository;
        this.fileUploadRepository = fileUploadRepository;
    }

    // ---------- 检索类 API ----------

    /**
     * 带权限的混合检索（向量 + BM25）
     */
    public List<SearchResult> hybridSearch(String query, String userId, int topK) {
        return hybridSearchService.searchWithPermission(query, userId, topK);
    }

    /**
     * 混合检索，默认 topK=10
     */
    public List<SearchResult> hybridSearch(String query, String userId) {
        return hybridSearch(query, userId, 10);
    }

    // ---------- 知识图谱类 API ----------

    /**
     * 知识图谱子图/关系查询
     */
    public String kgSubgraph(String query, Map<String, String> slots) {
        return knowledgeGraphService.getSubgraphContext(query, slots != null ? slots : Collections.emptyMap());
    }

    // ---------- 文档/Chunk 类 API ----------

    /**
     * 按文件 MD5 获取该文件下所有 chunk（需调用方已做权限校验时传入的 userId 与 file 归属一致或为管理员）
     */
    public List<ChunkDto> getChunksByFileMd5(String fileMd5, String userId) {
        Optional<FileUpload> fileOpt = fileUploadRepository.findByFileMd5AndUserId(fileMd5, userId);
        if (fileOpt.isEmpty()) {
            fileOpt = fileUploadRepository.findByFileMd5(fileMd5);
            if (fileOpt.isEmpty()) return List.of();
            FileUpload f = fileOpt.get();
            if (!f.getUserId().equals(userId) && !Boolean.TRUE.equals(f.isPublic())) return List.of();
        }
        List<DocumentVector> list = documentVectorRepository.findByFileMd5(fileMd5);
        return list.stream()
                .map(v -> new ChunkDto(v.getChunkId(), v.getTextContent(), v.getFileMd5()))
                .collect(Collectors.toList());
    }

    /**
     * 获取单个 chunk 详情
     */
    public Optional<ChunkDto> getChunkDetail(String fileMd5, int chunkId, String userId) {
        List<ChunkDto> chunks = getChunksByFileMd5(fileMd5, userId);
        return chunks.stream().filter(c -> c.getChunkId() != null && c.getChunkId() == chunkId).findFirst();
    }

    /**
     * 知识引擎 Chunk 简单 DTO
     */
    public static class ChunkDto {
        private Integer chunkId;
        private String textContent;
        private String fileMd5;

        public ChunkDto(Integer chunkId, String textContent, String fileMd5) {
            this.chunkId = chunkId;
            this.textContent = textContent;
            this.fileMd5 = fileMd5;
        }

        public Integer getChunkId() { return chunkId; }
        public void setChunkId(Integer chunkId) { this.chunkId = chunkId; }
        public String getTextContent() { return textContent; }
        public void setTextContent(String textContent) { this.textContent = textContent; }
        public String getFileMd5() { return fileMd5; }
        public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
    }
}
