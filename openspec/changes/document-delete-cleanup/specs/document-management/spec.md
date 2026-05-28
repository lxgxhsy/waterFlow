# Document Management Spec Delta

## MODIFIED Requirements

### Requirement: Delete Document

系统必须支持用户删除自己有权限管理的知识库文档，并确保删除后该文档不再被下载、预览、检索或作为知识库上下文参与回答。

#### Scenario: Owner deletes an uploaded document

- **GIVEN** 用户已登录
- **AND** 用户上传过文件 `fileMd5`
- **WHEN** 用户调用 `DELETE /api/v1/documents/{fileMd5}`
- **THEN** 系统返回 `200`
- **AND** 文件列表不再返回该文档
- **AND** MySQL 中该文档的解析切片被删除
- **AND** Elasticsearch 中该 `fileMd5` 的索引文档被删除
- **AND** MinIO 中该文档的合并对象被删除
- **AND** Redis 中该文档的上传状态被删除

#### Scenario: Admin deletes another user's document

- **GIVEN** 管理员已登录
- **AND** 目标文档由其他用户上传
- **WHEN** 管理员调用 `DELETE /api/v1/documents/{fileMd5}`
- **THEN** 系统允许删除
- **AND** 删除结果与文件所有者删除保持一致

#### Scenario: User deletes a document without permission

- **GIVEN** 普通用户已登录
- **AND** 目标文档不属于该用户且该用户无管理权限
- **WHEN** 用户调用 `DELETE /api/v1/documents/{fileMd5}`
- **THEN** 系统返回 `403`
- **AND** 不删除 MySQL、Elasticsearch、MinIO、Redis 中的数据

#### Scenario: Document does not exist

- **GIVEN** 用户已登录
- **AND** `fileMd5` 不存在
- **WHEN** 用户调用 `DELETE /api/v1/documents/{fileMd5}`
- **THEN** 系统返回 `404`
- **AND** 响应说明文档不存在

#### Scenario: External object already deleted

- **GIVEN** 用户已登录
- **AND** MySQL 中存在文件记录
- **AND** MinIO 或 Elasticsearch 中对应对象已不存在
- **WHEN** 用户调用 `DELETE /api/v1/documents/{fileMd5}`
- **THEN** 系统将不存在的外部对象视为已清理
- **AND** 删除流程继续执行

#### Scenario: External cleanup fails

- **GIVEN** 用户已登录
- **AND** 用户有权限删除文档
- **AND** Elasticsearch、MinIO 或 Redis 出现不可恢复错误
- **WHEN** 用户调用 `DELETE /api/v1/documents/{fileMd5}`
- **THEN** 系统返回 `500`
- **AND** 日志记录失败存储和 `fileMd5`
- **AND** 不记录 JWT、API Key 或预签名 URL

### Requirement: Delete Feedback

前端必须在删除前提示用户该操作会从知识库和文件存储中移除文档，并在删除成功后刷新列表。

#### Scenario: Delete succeeds

- **GIVEN** 用户在文件列表点击删除
- **WHEN** 用户确认删除
- **AND** 后端返回 `200`
- **THEN** 前端提示删除成功
- **AND** 刷新文件列表

#### Scenario: Delete fails

- **GIVEN** 用户在文件列表点击删除
- **WHEN** 用户确认删除
- **AND** 后端返回非 `200`
- **THEN** 前端显示后端错误信息
- **AND** 保留当前列表状态或刷新列表以校准状态

