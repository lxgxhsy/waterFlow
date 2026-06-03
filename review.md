STATUS: APPROVED
ROUND: 10
REVIEWED_SHA: 8b8a5f34506abc84a4eadfd6a79c5d97a301e6e8

## 本轮任务
- [x] 实现 PRD Phase 4 上下文增强：命中子 chunk 后，已补充父 chunk 或相邻 chunk 作为回答上下文，避免只返回孤立片段导致答案不完整。
- [x] 保持检索排序边界：相邻/父 chunk 只用于上下文组装和溯源增强，不污染 RRF/reranker 候选排序分数，也不会把无权限 chunk 拼入上下文。
- [x] 增强来源元数据：搜索结果和上下文组装结果已带文件名、chunkId，并为章节标题、页码、条款编号、父 chunk 建立字段和 ES 映射，便于回答引用更准确。
- [x] 增加配置开关和窗口大小：默认开启安全小窗口（前后各 1 个 chunk），支持关闭上下文扩展以排查召回噪声和性能问题。
- [x] 补充测试：已覆盖命中 chunk 后补相邻/父 chunk、权限过滤不被绕过、上下文扩展不改变原 topK 排序和分数、配置关闭时行为与当前一致。
- [x] 验证命令已通过：`mvn test -Dtest=HybridSearchServiceTest`。
