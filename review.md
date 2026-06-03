STATUS: CHANGES_REQUESTED
ROUND: 7
REVIEWED_SHA: 1e2daaa553c6c66db9166aee854827d9d9487256

## 本轮任务
- [ ] 实现 PRD Phase 3 reranker 精排链路：RRF 后保留可配置候选窗口（默认 top 50），再由 reranker 精排输出最终 topK，不能继续只把 topK 直接送入 reranker。
- [ ] 增加 reranker 配置开关：默认关闭，不依赖外部 reranker 服务；开启后才调用 reranker，并支持候选数量配置。
- [ ] 输出 reranker score：reranker 返回结果时应把最终精排分写入 `SearchResult.score`，关闭或失败时保留 RRF score。
- [ ] 增加降级保护：reranker 返回 null、抛异常或未配置实现时，应回退到 RRF 排序，不影响检索接口可用性。
- [ ] 补充单元测试：覆盖关闭时不调用 reranker、开启时 reranker 接收 top50 候选并最终截断 topK、reranker score 生效、reranker 失败回退 RRF。
- [ ] 验证命令：至少通过 `mvn test -Dtest=HybridSearchServiceTest`。
