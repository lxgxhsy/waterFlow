STATUS: CHANGES_REQUESTED
ROUND: 9
REVIEWED_SHA: 27ca1a16d27c5cedf7120ff23fe485ecce47a30d

## 本轮任务
- [ ] 实现 PRD Phase 4 上下文增强：命中子 chunk 后，补充父 chunk 或相邻 chunk 作为回答上下文，避免只返回孤立片段导致答案不完整。
- [ ] 保持检索排序边界：相邻/父 chunk 只用于上下文组装和溯源增强，不应污染 RRF/reranker 的候选排序分数，也不应把无权限 chunk 拼入上下文。
- [ ] 增强来源元数据：搜索结果或上下文组装结果应尽量带文件名、chunkId，并为章节标题、页码、条款编号预留字段或映射路径，便于回答引用更准确。
- [ ] 增加配置开关和窗口大小：默认开启安全的小窗口（如前后各 1 个 chunk）或可配置；必须能关闭上下文扩展以便排查召回噪声和性能问题。
- [ ] 补充测试：覆盖命中 chunk 后补相邻/父 chunk、权限过滤不被绕过、上下文扩展不改变原 topK 排序、配置关闭时行为与当前一致。
- [ ] 验证命令：至少通过 `mvn test -Dtest=HybridSearchServiceTest`；如新增独立上下文组装服务，则补跑对应测试类。
