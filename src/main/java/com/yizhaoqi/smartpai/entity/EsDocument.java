package com.yizhaoqi.smartpai.entity;


import lombok.Data;

/**
 * Elasticsearch存储的文档实体类
 * 包含文档内容和权限信息
 */
@Data
public class EsDocument {

    private String id;             // 文档唯一标识
    private String fileMd5;        // 文件指纹
    private Integer chunkId;       // 文本分块序号
    private String textContent;    // 文本内容
    private String metaSummary;    // 轻量级元数据摘要，用于避免跨记录拼接
    private float[] vector;        // 向量数据（768维）
    private String modelVersion;   // 向量生成模型版本
    private String userId;         // 上传用户ID
    private String orgTag;         // 组织标签
    private boolean isPublic;      // 是否公开
    private String sectionTitle;   // 章节标题，供引用溯源使用
    private Integer pageNumber;    // 页码，供引用溯源使用
    private String clauseNumber;   // 条款编号，供引用溯源使用
    private Integer parentChunkId; // 父 chunk，供层级上下文扩展使用

    /**
     * 默认构造函数，用于Jackson反序列化
     */
    public EsDocument() {
    }

    /**
     * 完整构造函数，包含权限字段
     */
    public EsDocument(String id, String fileMd5, int chunkId, String content,
                     String metaSummary, float[] vector, String modelVersion,
                     String userId, String orgTag, boolean isPublic) {
        this(id, fileMd5, chunkId, content, metaSummary, vector, modelVersion,
                userId, orgTag, isPublic, null, null, null, null);
    }

    public EsDocument(String id, String fileMd5, int chunkId, String content,
                     String metaSummary, float[] vector, String modelVersion,
                     String userId, String orgTag, boolean isPublic,
                     String sectionTitle, Integer pageNumber, String clauseNumber,
                     Integer parentChunkId) {
        this.id = id;
        this.fileMd5 = fileMd5;
        this.chunkId = chunkId;
        this.textContent = content;
        this.metaSummary = metaSummary;
        this.vector = vector;
        this.modelVersion = modelVersion;
        this.userId = userId;
        this.orgTag = orgTag;
        this.isPublic = isPublic;
        this.sectionTitle = sectionTitle;
        this.pageNumber = pageNumber;
        this.clauseNumber = clauseNumber;
        this.parentChunkId = parentChunkId;
    }
    

}
