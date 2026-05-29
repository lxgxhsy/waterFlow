# 垂域混合检索 Benchmark

## 目的

该 benchmark 用于评估木瓜水库垂域知识库检索效果，服务于“垂域混合检索召回优化”PRD 的 PR0 baseline。

当前重点不是评估最终大模型回答，而是评估检索阶段是否能把正确证据片段召回到前排。

## 数据来源

- 文档：`屯溪流域木瓜水库4330127000297.pdf`
- 索引：`knowledge_base`
- 接口：`GET /api/v1/search/hybrid`
- benchmark 数据文件：`docs/eval/hybrid-search-benchmark.json`

## 问题类型覆盖

| 类型 | 数量 | 目的 |
| --- | ---: | --- |
| 简单事实 | 4 | 验证实体属性、基础数值召回 |
| 术语不一致 | 2 | 验证用户问法和文档表达不一致时的召回 |
| 数值精确 | 2 | 验证精确水位、闸门等数值召回 |
| 多跳推理 | 3 | 验证需要组合条件、操作、阈值的问题 |
| 表格查询 | 3 | 验证表格类数值召回 |
| 边界无答案 | 2 | 验证文档未提及时不应强答 |
| 实体细节 | 2 | 验证责任人、行政村列表等实体召回 |
| 多跳+计算 | 1 | 验证数值和原因解释组合 |
| 跨文档推理 | 1 | 验证附录/跨章节信息召回 |

## 评估字段

每条样例包含：

- `id`：样例编号。
- `type`：问题类型。
- `query`：用户问题。
- `groundTruthAnswer`：答案级 Ground Truth。
- `expectedEvidence`：证据描述，用于人工判断检索结果是否命中。
- `requiredTerms`：弱自动评估关键词，用于 baseline 脚本粗略判断证据命中。
- `notes`：考察点说明。

## 指标定义

PR0 baseline 先输出弱自动指标，后续可在人工标注 `relevantChunkIds` 后升级为严格指标。

### Top1 Evidence Hit

Top1 检索片段是否命中该问题的 `requiredTerms`。

### Recall@5 / Recall@10

Top5 / Top10 中是否至少有一个片段命中该问题的 `requiredTerms`。

当前为弱自动判断：

```text
命中 = 片段文本包含任意一个 requiredTerms
```

后续严格版本应改为：

```text
命中 = 检索结果 chunkId 属于人工标注 relevantChunkIds
```

### MRR@10

Top10 内第一个命中片段的倒数排名。

```text
MRR = 1 / firstRelevantRank
```

如果 Top10 无命中，则记为 0。

### No-answer Safety

对于边界无答案问题，检索阶段不一定要求无结果，但最终回答阶段必须能基于证据不足回答“文档未提及”。PR0 仅记录是否召回明显误导性片段，后续 PR 应补充回答链路评估。

## 运行方式

启动后端后执行：

```powershell
.\scripts\eval-hybrid-search-baseline.ps1
```

默认参数：

```powershell
.\scripts\eval-hybrid-search-baseline.ps1 `
  -BaseUrl "http://localhost:8081" `
  -Username "admin" `
  -Password "admin123" `
  -TopK 10
```

脚本会读取 `docs/eval/hybrid-search-benchmark.json`，调用当前 `/api/v1/search/hybrid`，并输出 baseline 报告。

## 注意事项

1. `requiredTerms` 是弱自动评估，可能高估或低估真实效果。
2. 正式 Recall@K / MRR / nDCG 需要人工标注每个问题的相关 `chunkId`。
3. 当前 benchmark 主要用于比较同一数据集下不同检索方案的相对变化。
4. 边界无答案问题应结合最终回答评估，不能只看检索命中。
