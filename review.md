STATUS: CHANGES_REQUESTED
ROUND: 5
REVIEWED_SHA: 4881616dcb29f5147b8096c5e0083306e9752ff7

## 本轮任务
- [x] 修复匿名搜索权限：`HybridSearchService.search(query, topK)` 现在通过 `isPublic=true` 权限过滤限制匿名/兼容搜索范围。
- [x] 补充权限过滤测试：已验证 BM25 召回和向量召回请求都携带 `userId/isPublic/orgTag` 权限 filter，并覆盖匿名 public-only filter。
- [ ] 将评测框架从固定 fixture 推进到可验证真实检索链路：当前 `SearchEvaluation.fixtureRouteResults` 仍用内置样例直接喂给 `mergeByRrf`，只能证明指标计算和文本片段匹配逻辑正确，不能证明真实 ES/BM25/vector 召回能命中 `307.00m`、`310.00m`、`开闸/放水/泄洪` 等 PRD 验收内容。
- [x] 补充性能验证或优化说明：双路召回已改为 `CompletableFuture` 并行执行，消除了本轮串行召回缺口。
- [x] 明确 reranker 预留点：已新增 `SearchReranker` 扩展接口，并在 RRF merge 后通过 `applyReranker` 接入。
