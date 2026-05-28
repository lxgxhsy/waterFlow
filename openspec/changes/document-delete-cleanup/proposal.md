# Change: document-delete-cleanup

## Summary

删除知识库文档时，需要将该文档从业务元数据、解析切片、检索索引、对象存储和上传缓存中一致移除，避免出现已删除文档仍可检索、仍可下载、或续传状态残留的问题。

## Motivation

当前文档删除链路已经覆盖部分存储，但删除语义不够完整：

- Elasticsearch 中的向量索引必须随文档删除，否则知识库检索仍可能命中旧内容。
- MinIO 中的合并文件和可能残留的分片对象必须清理，否则原始文件仍然占用存储并存在数据泄露风险。
- MySQL 中的 `file_upload` 和 `document_vectors` 需要与删除结果保持一致。
- Redis 中的上传进度、续传状态或会话缓存需要清理，避免前端显示错误状态。
- 管理员删除、重复删除、局部删除失败等边界行为需要明确。

## Scope

In scope:

- `DELETE /api/v1/documents/{fileMd5}` 的删除语义。
- `DocumentController` 与 `DocumentService` 的职责边界。
- MySQL、Elasticsearch、MinIO、Redis 的清理要求。
- 删除失败时的返回格式、日志与可观测性。
- 与前端删除按钮的交互反馈。

Out of scope:

- 批量删除。
- 回收站/恢复能力。
- 定时全量数据修复任务。
- 文档重新向量化接口。

## Codebase Conventions

实现必须符合当前项目风格：

- Controller 只做参数接收、权限入口校验、统一响应和业务日志。
- Service 承担删除编排，保持主要业务逻辑在 `DocumentService`。
- 持久层访问通过现有 Repository、Service 或明确命名的新方法完成。
- 使用现有响应结构：`code`、`message`、`data`。
- 使用现有日志工具 `LogUtils` 和类内 `logger`，不要直接打印敏感信息。
- 不把 API Key、对象预签名 URL、JWT 等敏感信息写入日志。
- 不引入新的框架级依赖，除非当前能力无法通过已有 Spring/Data/MinIO/ES/Redis API 实现。

## Risks

- MySQL 事务无法覆盖 ES、MinIO、Redis，删除过程存在跨存储局部失败。
- 如果先删除 MySQL 再删外部存储，失败后会丢失定位外部对象所需元数据。
- 如果先删除外部存储再删 MySQL，接口失败时前端可能看到仍存在但不可下载的文件。
- MinIO 分片路径规则如果不统一，可能出现残留分片。

## Decision

采用“先读取元数据、再清理外部存储、最后删除/标记 MySQL”的编排策略：

1. 根据 `fileMd5` 查询文件元数据并校验权限。
2. 清理 Elasticsearch 中该 `fileMd5` 的索引文档。
3. 清理 MinIO 中 `merged/{fileName}` 和该文件相关分片对象。
4. 清理 Redis 中该用户和文件相关的上传状态缓存。
5. 删除 MySQL 中 `document_vectors` 和 `file_upload` 记录。
6. 返回删除结果，并记录每个存储的清理状态。

删除接口应尽量幂等。对于已经不存在的外部对象，视为删除成功；对于权限内但 MySQL 记录不存在的文档，返回 `404`。

