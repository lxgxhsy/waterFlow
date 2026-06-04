STATUS: APPROVED
ROUND: 12
REVIEWED_SHA: da8a93e057cbf8de0f14cc887acea64653dc8ef6

## 本轮任务
- [x] 补强 PRD Phase 2/验收评测：评测集已覆盖原则类、水位类、操作类、位置类、概况类、时间/汛期类，并标注期望命中文档、chunkId/关键片段、章节或条款线索。
- [x] 完善离线指标输出：在现有 Recall@5、Recall@10、MRR@10 基础上已补齐 nDCG@10、命中文档率、命中正确章节率，并支持每个 case 输出命中明细，便于定位失败样本。
- [x] 增加优化前后/配置对比能力：已支持对比 BM25-only、RRF、RRF+reranker、上下文扩展开关等关键配置的指标结果，避免只能看单次当前实现分数。
- [x] 增加性能评测或基准入口：已增加离线 benchmark 入口，统计平均耗时和 P95，覆盖默认 topK=10、不启用 reranker、启用 reranker/上下文扩展等场景。
- [x] 保持评测可离线运行：默认测试不依赖真实 Elasticsearch、外部 embedding/reranker/LLM 服务；真实服务评测入口通过 disabled/manual 类隔离。
- [x] 补充测试和验证命令已通过：`mvn test -Dtest=HybridSearchServiceTest`。
