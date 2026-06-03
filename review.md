STATUS: CHANGES_REQUESTED
ROUND: 4
REVIEWED_SHA: 24fed199a42e346b76b16ed76536d9552e3a258f

## 本轮任务
- [ ] 修复匿名搜索权限：`SearchController` 无 userId 分支声称仅公开内容，但 `HybridSearchService.search(query, topK)` 当前通过 `match_all` 权限查询返回全量文档，应限制为仅 `isPublic=true` 或统一走安全的公开检索路径。
- [ ] 补充权限过滤测试：验证 BM25 召回和向量召回两路都带 `userId/isPublic/orgTag` 权限 filter，避免双路召回后任一路绕过权限导致泄露。
- [ ] 将评测框架从 mock 占位推进到可验证真实召回效果：PRD 验收问题集的 expected keys/片段目前是占位，不能证明 `307.00m`、`310.00m`、`开闸/放水/泄洪` 等真实数据可命中。
- [ ] 补充性能验证或优化说明：双路召回当前串行执行，尚无默认 topK=10 下 P95 数据；需要验证是否满足 PRD 的 P95 < 1s / < 1.5s 目标，或改为并行召回。
- [ ] 明确 reranker 预留点：当前 Phase 1 可不接 reranker，但 PRD 要求为后续精排预留接口/扩展点，建议补接口或在设计中明确接入位置。
