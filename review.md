STATUS: CHANGES_REQUESTED
ROUND: 3
REVIEWED_SHA: 5c0ae4b

## 任务：PR3 — 检索评测集（PRD §9 / §11 Phase 2 / §14 PR3）

目标：建立垂域检索效果评测体系，让召回质量可量化、可回归。

### 清单

- [ ] 新建 `src/test/java/com/yizhaoqi/smartpai/service/SearchEvaluation.java`，不作为 `mvn test` 默认执行的单元测试（用 `@Disabled` 或放在单独目录），而是一个可手动运行的评测工具类
- [ ] 定义评测数据结构：`EvalCase(String query, List<String> expectedChunkKeys)` —— `chunkKey = fileMd5:chunkId`
- [ ] 内置 PRD §9.3 验收问题集（至少 10 条），每条标注期望命中的关键词或片段特征（暂用占位 expectedChunkKeys，后续填真实值）
- [ ] 实现 `Recall@K` 计算：给定一组评测 case，调用 `expandQueryForRecall` 得到扩展 query，模拟 BM25+向量双路结果输入 `mergeByRrf`，统计 topK 中命中 expected 的比例
- [ ] 实现 `MRR@K` 计算：第一个命中的排名倒数
- [ ] 输出格式化评测报告（stdout），包含每条 case 的 query、命中情况、Recall@5、Recall@10、MRR@10，以及整体平均值
- [ ] 在 `HybridSearchServiceTest.java` 中新增一个 `@Test` 验证评测框架本身能跑通（用 mock 数据，不依赖 ES）

### 约束

- 不依赖 ES 连接（评测框架用 mock 数据验证逻辑正确性）
- 不改动现有业务代码（`HybridSearchService.java` 不动）
- `expandQueryForRecall` 和 `mergeByRrf` 可直接调用（package-private 可见性已有）
- PRD §9.3 验收问题集：

```
1. 水库有哪些原则？
2. 水库的调度原则有哪些？
3. 木瓜水库汛限水位是多少？
4. 梅汛期限制水位是多少？
5. 台汛期限制水位是多少？
6. 非汛期水库怎么运行？
7. 什么时候需要开闸放水？
8. 水库以什么为主？
9. 水库兼有哪些功能？
10. 木瓜水库防洪保护对象有哪些？
```

### 验收

- `mvn test` 全绿（评测工具类本身不在默认 test 中执行，但框架验证测试要过）
- 手动运行评测类能输出格式化报告
