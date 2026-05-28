# Tasks

## 1. Backend Behavior

- [ ] 确认 `DELETE /api/v1/documents/{fileMd5}` 支持文件所有者删除。
- [ ] 修正管理员删除语义：管理员可以删除非本人文件。
- [ ] 删除前读取并保留 `fileName`、`userId`、`orgTag`、`isPublic` 等元数据用于后续清理和日志。
- [ ] 删除 Elasticsearch 中 `fileMd5` 对应的全部文档。
- [ ] 删除 MinIO 中 `merged/{fileName}`。
- [ ] 删除 MinIO 中该 `fileMd5` 相关的分片对象。
- [ ] 删除 Redis 中 `upload:{userId}:{fileMd5}` 上传状态。
- [ ] 删除 MySQL 中 `document_vectors.file_md5 = fileMd5` 的切片记录。
- [ ] 删除或软删除 MySQL 中 `file_upload.file_md5 = fileMd5` 的文件记录。

## 2. Error Handling

- [ ] 外部存储对象不存在时按删除成功处理。
- [ ] 权限不足返回 `403`。
- [ ] 文件不存在返回 `404`。
- [ ] 删除过程出现不可恢复错误时返回 `500`，响应中不暴露敏感内部细节。
- [ ] 日志记录各存储清理状态，但不记录 JWT、API Key、预签名 URL。

## 3. Frontend Behavior

- [ ] 删除按钮二次确认文案明确“会从知识库和文件存储中移除”。
- [ ] 删除成功后刷新文件列表。
- [ ] 删除失败时显示后端返回的错误信息。

## 4. Verification

- [ ] 删除后 `/api/v1/documents/uploads` 不再返回该文件。
- [ ] 删除后 `document_vectors` 不再存在该 `fileMd5` 的记录。
- [ ] 删除后 ES `knowledge_base` 不再命中该 `fileMd5`。
- [ ] 删除后 MinIO 中 `merged/{fileName}` 不存在。
- [ ] 删除后 Redis 中 `upload:{userId}:{fileMd5}` 不存在。
- [ ] 重复删除同一文件返回稳定结果。

