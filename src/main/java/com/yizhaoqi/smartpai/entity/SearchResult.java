package com.yizhaoqi.smartpai.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SearchResult {
    private String fileMd5;    // 文件指纹
    private Integer chunkId;   // 文本分块序号
    private String textContent; // 文本内容
    private Double score;      // 搜索得分
    private String fileName;   // 原始文件名
    private String userId;     // 上传用户ID
    private String orgTag;     // 组织标签
    private Boolean isPublic;  // 是否公开
    private String sectionTitle; // 章节标题，预留给答案引用
    private Integer pageNumber;  // 页码，预留给答案引用
    private String clauseNumber; // 条款编号，预留给答案引用
    private Integer parentChunkId; // 父 chunk，预留给层级切片
    private List<ContextChunk> contextChunks = new ArrayList<>(); // 回答上下文片段，不参与排序

    public SearchResult(String fileMd5, Integer chunkId, String textContent, Double score) {
        this(fileMd5, chunkId, textContent, score, null, null, false, null);
    }

    public SearchResult(String fileMd5, Integer chunkId, String textContent, Double score, String fileName) {
        this(fileMd5, chunkId, textContent, score, null, null, false, fileName);
    }

    public SearchResult(String fileMd5, Integer chunkId, String textContent, Double score, String userId, String orgTag, boolean isPublic) {
        this(fileMd5, chunkId, textContent, score, userId, orgTag, isPublic, null);
    }

    public SearchResult(String fileMd5, Integer chunkId, String textContent, Double score, String userId, String orgTag, boolean isPublic, String fileName) {
        this.fileMd5 = fileMd5;
        this.chunkId = chunkId;
        this.textContent = textContent;
        this.score = score;
        this.userId = userId;
        this.orgTag = orgTag;
        this.isPublic = isPublic;
        this.fileName = fileName;
    }

    @Data
    public static class ContextChunk {
        private String fileMd5;
        private Integer chunkId;
        private String textContent;
        private String fileName;
        private String sectionTitle;
        private Integer pageNumber;
        private String clauseNumber;
        private Integer parentChunkId;
        private String relation;

        public ContextChunk(String fileMd5,
                            Integer chunkId,
                            String textContent,
                            String fileName,
                            String sectionTitle,
                            Integer pageNumber,
                            String clauseNumber,
                            Integer parentChunkId,
                            String relation) {
            this.fileMd5 = fileMd5;
            this.chunkId = chunkId;
            this.textContent = textContent;
            this.fileName = fileName;
            this.sectionTitle = sectionTitle;
            this.pageNumber = pageNumber;
            this.clauseNumber = clauseNumber;
            this.parentChunkId = parentChunkId;
            this.relation = relation;
        }
    }
}
