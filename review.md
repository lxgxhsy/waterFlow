STATUS: CHANGES_REQUESTED
ROUND: 1
TARGET: src/main/java/com/yizhaoqi/smartpai/service/HybridSearchService.java
TEST: src/test/java/com/yizhaoqi/smartpai/service/HybridSearchServiceTest.java
SPEC: docs/prd/垂域混合检索召回优化.md

# 判定

当前 HEAD (067b453) 只完成 PRD §14 中 PR2 的一部分（query expansion，且不完整）。
PRD §15 强调的核心范式改造——双路召回 + RRF 融合（PR1）——完全未实现，
检索链路仍是 PRD §2.1 / §8.1 点名要修的旧模式。

# 逐条对照（PRD 条款）

- §7.1 双路召回：向量召回仍 .must(match textContent) 强过滤 —— 未做（PRD 要求向量召回去掉 must match）
- §7.3 RRF 融合：仍 queryWeight=0.2 + rescoreQueryWeight=1.0 原始分混合 —— 未做
- §7.5 fallback：textOnlySearchWithPermission 仍 .minScore(0.3d) —— 未做（要求移除或降到 0.05）
- §8   排序策略：BM25 仍主导 —— 未做
- §13  方法拆分：searchBm25WithPermission / searchVectorWithPermission / mergeByRrf / buildPermissionFilter 均不存在 —— 未做
- §7.2 权限过滤：逻辑在两处重复手写，未抽统一方法（§12.4 防遗漏）—— 待重构
- §7.4 query expansion：已实现但不完整 —— 见缺口

# §7.4 query expansion 缺口

- 原则类：缺 兴利、灌溉
- 水位类：缺 控制水位、库水位、307.00m、310.00m、314.00m、298.00m
- 操作类：触发词混入过宽的 操作 / 怎么处理 / 如何处理；扩展词含 PRD 没有的 放空管、电站满发；缺 调蓄、降低水位、溢洪道

# 任务（按 PRD §14 顺序，commit 拆分）

## commit 1 — PR1：双路召回 + RRF
1. 新增 buildPermissionQuery(userDbId, tags) → Query，BM25 路与向量路共用（§13 / §12.4）
2. 新增 searchBm25WithPermission(recallQuery, userDbId, tags, recallK)：match must + 权限 filter，recallK=topK*20，无 minScore
3. 新增 searchVectorWithPermission(queryVector, userDbId, tags, recallK)：KNN 去掉 must match，仅权限 filter，recallK=topK*30，numCandidates=topK*50
4. 新增 mergeByRrf(bm25, vector, topK)：finalScore=Σ weight/(60+rank)，rank 从 1 起，vec=1.0 / bm25=0.8，去重 key=fileMd5:chunkId，两路命中累加，降序取 topK，写入 score 字段，不 mutate 入参
5. 改写 searchWithPermission 走双路+RRF；queryVector==null 退化 textOnlySearchWithPermission；删除旧 must match + rescore 逻辑
6. search（无权限）复用上述方法，权限参数传 null/空
7. textOnlySearchWithPermission 移除 minScore(0.3)（§7.5）

## commit 2 — PR2：query expansion 补齐
8. 原则类补 兴利、灌溉
9. 水位类补 控制水位、库水位、307.00m、310.00m、314.00m、298.00m
10. 操作类触发词移除 操作/怎么处理/如何处理；扩展词删 放空管/电站满发，加 调蓄/降低水位/溢洪道

## 测试
11. mergeByRrf 单测：两路累加 / 单路 / topK 截断 / 降序 / 空输入 / rank 从 1 起
12. expandQueryForRecall：更新操作类断言（删 放空管/电站满发，加 调蓄/降低水位/溢洪道），补水位类/原则类新词断言

# 约束

- 对外签名不变：searchWithPermission(String,String,int)、search(String,int)
- 不引入新依赖；mergeByRrf 纯内存
- SearchResult 字段：fileMd5, chunkId, textContent, score, userId, orgTag, isPublic, fileName

# 验收闸（§10）

- mvn test 全绿
- 向量路无 must match textContent
- 两路共用同一个权限 filter
- embedding 失败仍返回非空 BM25 结果
- 权限不绕过、不泄露无权限文档
