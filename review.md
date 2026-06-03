STATUS: APPROVED
ROUND: 6
REVIEWED_SHA: 1e2daaa553c6c66db9166aee854827d9d9487256

## 本轮任务
- [x] 修复匿名搜索权限：`HybridSearchService.search(query, topK)` 现在通过 `isPublic=true` 权限过滤限制匿名/兼容搜索范围。
- [x] 补充权限过滤测试：已验证 BM25 召回和向量召回请求都携带 `userId/isPublic/orgTag` 权限 filter，并覆盖匿名 public-only filter。
- [x] 将评测框架从固定 fixture 推进到可验证检索链路：`SearchEvaluation.evaluateWithSearch` 现在调用 `HybridSearchService.search`，测试通过 in-memory recall service 覆盖 BM25 query expansion、vector recall 和 RRF 后的 PRD 验收片段命中。
- [x] 补充性能验证或优化说明：双路召回已改为 `CompletableFuture` 并行执行，消除了本轮串行召回缺口。
- [x] 明确 reranker 预留点：已新增 `SearchReranker` 扩展接口，并在 RRF merge 后通过 `applyReranker` 接入。
