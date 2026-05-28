# Design

## Current State

当前 `DocumentController.deleteDocument` 负责入口权限判断，并调用 `DocumentService.deleteDocument(fileMd5, userId)`。

当前 `DocumentService.deleteDocument` 已尝试清理：

- Elasticsearch：`elasticsearchService.deleteByFileMd5(fileMd5)`
- MinIO：`merged/{fileName}`
- MySQL：`document_vectors`、`file_upload`

主要缺口：

- Redis 上传状态未清理。
- MinIO 分片对象未清理。
- Service 内部使用 `findByFileMd5AndUserId(fileMd5, userId)`，会让管理员删除他人文档的语义失效。
- 外部存储删除失败后仍继续删除 MySQL，可能造成残留不可定位。
- 删除结果没有暴露各存储清理状态，排障成本较高。

## Proposed Flow

```text
DELETE /api/v1/documents/{fileMd5}
  -> DocumentController
    -> 解析 userId / role
    -> DocumentService.deleteDocument(fileMd5, userId, role)
      -> 查询 FileUpload
      -> 校验 owner/admin 权限
      -> 删除 ES 文档
      -> 删除 MinIO merged 对象
      -> 删除 MinIO chunk 对象
      -> 删除 Redis 上传状态
      -> 删除 MySQL document_vectors
      -> 删除 MySQL file_upload
      -> 返回 DeleteDocumentResult
```

## Deletion Semantics

### MySQL

`document_vectors` 存放解析后的文本切片，必须删除。

`file_upload` 记录可以有两种策略：

- 当前版本：物理删除，保持现有项目行为。
- 后续增强：软删除，增加 `deleted_at` 或 `status=DELETED` 保留审计。

本次变更按现有表结构先采用物理删除。

### Elasticsearch

按 `fileMd5` 删除 `knowledge_base` 索引中的全部文档。

如果 ES 中没有匹配文档，视为成功。

### MinIO

删除合并后的文件：

```text
merged/{fileName}
```

删除可能残留的分片对象。具体路径应以 `UploadService` 的实际写入规则为准，优先复用已有路径生成逻辑，避免硬编码重复规则。

MinIO 对象不存在视为成功。

### Redis

删除上传状态 key：

```text
upload:{userId}:{fileMd5}
```

如后续存在更多文档级缓存，应统一通过明确前缀清理，避免模糊扫描全库。

## Failure Policy

跨存储无法使用单一事务保证一致性。实现上应采用可重试、幂等删除。

建议策略：

- 元数据查询和权限校验失败：立即返回。
- ES/MinIO/Redis 清理失败：记录错误，返回失败，不删除 MySQL 主记录，保留后续重试能力。
- MySQL 删除失败：返回失败，外部存储可能已删除，日志必须记录状态。

## Result Shape

Service 可返回结构化结果供日志或响应使用：

```java
class DeleteDocumentResult {
    String fileMd5;
    String fileName;
    boolean mysqlDeleted;
    boolean vectorsDeleted;
    boolean elasticsearchDeleted;
    boolean minioMergedDeleted;
    boolean minioChunksDeleted;
    boolean redisDeleted;
}
```

接口响应可以保持简洁：

```json
{
  "code": 200,
  "message": "文档删除成功",
  "data": {
    "fileMd5": "..."
  }
}
```

详细状态写日志即可，避免前端依赖内部存储细节。

