STATUS: CHANGES_REQUESTED
ROUND: 2
REVIEWED_SHA: fe96cf6ccd8b4ebeea8c84cd898dd54632a18ddd

## 任务清单

- [ ] 新增一个 demo 单测，测试最新代码的核心行为

## 说明

在 `HybridSearchServiceTest.java` 中补充一个端到端风格的 demo 测试，
验证 `expandQueryForRecall` + `mergeByRrf` 协同工作的典型场景：
给定一个水库调度类问题，扩展后的 query 应包含预期关键词，
且 RRF 合并结果应按分数降序、去重正确。

命名建议：`demoHybridRecallFlowForReservoirScheduling`
