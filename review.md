STATUS: APPROVED
ROUND: 1
TARGET: src/main/java/com/yizhaoqi/smartpai/service/HybridSearchService.java
TEST: src/test/java/com/yizhaoqi/smartpai/service/HybridSearchServiceTest.java

## 复审结论

ROUND 1 全部任务已完成，符合 PRD §10 验收标准。

## 已通过项

- [x] §7.1 双路召回：searchVectorWithPermission 去掉 must match textContent
- [x] §7.3 RRF 融合：mergeByRrf 实现正确，rankConstant=60/vec=1.0/bm25=0.8
- [x] §7.5 fallback：textOnlySearchWithPermission 移除 minScore(0.3)
- [x] §8 排序策略：BM25 rescore 主导已替换为 RRF 排名融合
- [x] §12.4 权限不遗漏：buildPermissionQuery 统一构造，两路共用
- [x] §13 方法拆分：四个新方法均已实现
- [x] §7.4 query expansion：原则类补兴利/灌溉，水位类补数值，操作类收紧触发词

## 遗留细节（不阻塞 APPROVED，下轮可选处理）

- buildPermissionQuery 中公开文档字段用 isPublic，旧代码为 public，需确认 ES mapping 字段名一致
