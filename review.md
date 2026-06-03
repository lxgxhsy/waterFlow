STATUS: CHANGES_REQUESTED
ROUND: 11
REVIEWED_SHA: 8b8a5f34506abc84a4eadfd6a79c5d97a301e6e8

## 本轮任务
- [ ] 补强 PRD Phase 2/验收评测：评测集需覆盖原则类、水位类、操作类、位置类、概况类、时间/汛期类，并标注期望命中文档、chunkId/关键片段、章节或条款线索。
- [ ] 完善离线指标输出：在现有 Recall@5、Recall@10、MRR@10 基础上补齐 nDCG@10、命中文档率、命中正确章节率，并确保每个 case 可输出命中明细，便于定位失败样本。
- [ ] 增加优化前后/配置对比能力：至少能对比 BM25-only、RRF、RRF+reranker（如开启）、上下文扩展开关等关键配置的指标结果，避免只能看单次当前实现分数。
- [ ] 增加性能评测或基准入口：统计检索耗时（平均值、P95 或可复现的近似指标），覆盖默认 topK=10、不启用 reranker、启用 reranker/上下文扩展等场景，支撑 PRD 的 P95 验收目标。
- [ ] 保持评测可离线运行：默认测试不依赖真实 Elasticsearch、外部 embedding/reranker/LLM 服务；如提供真实服务评测入口，必须与单元测试隔离并有明确开关。
- [ ] 补充测试和验证命令：至少通过 `mvn test -Dtest=HybridSearchServiceTest`；如新增独立评测类/基准类，则补跑对应测试类并在 review.md 里记录命令。
