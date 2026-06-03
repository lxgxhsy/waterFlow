STATUS: APPROVED
ROUND: 2
REVIEWED_SHA: 5c0ae4b4ed947c9dc3d44e57cd7b5397acf923ea

## 复审结论

ROUND 2 任务已完成。

- [x] 新增 demo 单测 `demoHybridRecallFlowForReservoirScheduling`

测试覆盖了 expandQueryForRecall + mergeByRrf 的协同场景：
- 台汛期调度问题同时触发原则类、水位类、操作类三组扩展词（验证多规则联合触发）
- BM25 路含同 chunk 重复，向量路含跨文件命中，RRF 去重+累加+降序均正确
- 结果截断到 topK=3，排序用 Comparator.reverseOrder() 断言严格降序
