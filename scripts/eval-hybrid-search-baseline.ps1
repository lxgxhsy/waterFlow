param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [int]$TopK = 10,
    [string]$BenchmarkPath = "docs/eval/hybrid-search-benchmark.json",
    [string]$ReportPath = "docs/eval/hybrid-search-baseline.md"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Repair-Mojibake {
    param([AllowNull()][string]$Text)

    if ($null -eq $Text) {
        return $null
    }

    try {
        return [System.Text.Encoding]::UTF8.GetString([System.Text.Encoding]::GetEncoding("ISO-8859-1").GetBytes($Text))
    } catch {
        return $Text
    }
}

function Normalize-Text {
    param([AllowNull()][string]$Text)

    if ($null -eq $Text) {
        return ""
    }

    return (($Text -replace "\s+", " ").Trim())
}

function Test-TermHit {
    param(
        [string]$Text,
        [object[]]$Terms
    )

    if ($null -eq $Terms -or $Terms.Count -eq 0) {
        return $false
    }

    foreach ($term in $Terms) {
        if ([string]::IsNullOrWhiteSpace([string]$term)) {
            continue
        }
        if ($Text.Contains([string]$term)) {
            return $true
        }
    }

    return $false
}

function Escape-MarkdownCell {
    param([AllowNull()][string]$Text)

    if ($null -eq $Text) {
        return ""
    }

    return (($Text -replace "\|", "\|") -replace "`r?`n", " ")
}

if (-not (Test-Path $BenchmarkPath)) {
    throw "Benchmark file not found: $BenchmarkPath"
}

$benchmark = Get-Content $BenchmarkPath -Raw -Encoding UTF8 | ConvertFrom-Json

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/users/login" -Method Post -ContentType "application/json; charset=utf-8" -Body $loginBody -TimeoutSec 15
if ($login.code -ne 200 -or -not $login.data.token) {
    throw "Login failed: $($login | ConvertTo-Json -Depth 5)"
}

$headers = @{ Authorization = "Bearer $($login.data.token)" }
$evaluations = @()

foreach ($case in $benchmark) {
    $url = "$BaseUrl/api/v1/search/hybrid?query=$([uri]::EscapeDataString($case.query))&topK=$TopK"
    $sw = [Diagnostics.Stopwatch]::StartNew()

    try {
        $response = Invoke-RestMethod -Uri $url -Method Get -Headers $headers -TimeoutSec 60
        $sw.Stop()

        $results = @($response.data | Select-Object -First $TopK)
        $ranked = @()
        $hitRanks = @()

        for ($i = 0; $i -lt $results.Count; $i++) {
            $item = $results[$i]
            $text = Normalize-Text (Repair-Mojibake $item.textContent)
            $fileName = Repair-Mojibake $item.fileName
            $rank = $i + 1
            $isHit = Test-TermHit -Text $text -Terms $case.requiredTerms

            if ($isHit) {
                $hitRanks += $rank
            }

            $snippetLength = [Math]::Min(120, $text.Length)
            $snippet = if ($snippetLength -gt 0) { $text.Substring(0, $snippetLength) } else { "" }

            $ranked += [pscustomobject]@{
                rank = $rank
                chunkId = $item.chunkId
                score = if ($null -ne $item.score) { [Math]::Round([double]$item.score, 4) } else { $null }
                hit = $isHit
                fileName = $fileName
                snippet = $snippet
            }
        }

        $firstRelevantRank = if ($hitRanks.Count -gt 0) { ($hitRanks | Measure-Object -Minimum).Minimum } else { $null }
        $mrr = if ($null -ne $firstRelevantRank) { [Math]::Round(1.0 / [double]$firstRelevantRank, 4) } else { 0.0 }

        $evaluations += [pscustomobject]@{
            id = $case.id
            type = $case.type
            query = $case.query
            groundTruthAnswer = $case.groundTruthAnswer
            elapsedMs = $sw.ElapsedMilliseconds
            resultCount = $results.Count
            top1Hit = ($firstRelevantRank -eq 1)
            recallAt5 = (($hitRanks | Where-Object { $_ -le 5 } | Measure-Object).Count -gt 0)
            recallAt10 = ($hitRanks.Count -gt 0)
            firstRelevantRank = $firstRelevantRank
            mrrAt10 = $mrr
            topResults = $ranked
            error = $null
        }
    } catch {
        $sw.Stop()
        $evaluations += [pscustomobject]@{
            id = $case.id
            type = $case.type
            query = $case.query
            groundTruthAnswer = $case.groundTruthAnswer
            elapsedMs = $sw.ElapsedMilliseconds
            resultCount = 0
            top1Hit = $false
            recallAt5 = $false
            recallAt10 = $false
            firstRelevantRank = $null
            mrrAt10 = 0.0
            topResults = @()
            error = $_.Exception.Message
        }
    }
}

$total = $evaluations.Count
$top1 = @($evaluations | Where-Object { $_.top1Hit }).Count
$r5 = @($evaluations | Where-Object { $_.recallAt5 }).Count
$r10 = @($evaluations | Where-Object { $_.recallAt10 }).Count
$avgMrr = if ($total -gt 0) { [Math]::Round((($evaluations | Measure-Object -Property mrrAt10 -Average).Average), 4) } else { 0.0 }
$avgLatency = if ($total -gt 0) { [Math]::Round((($evaluations | Measure-Object -Property elapsedMs -Average).Average), 2) } else { 0.0 }
$sortedLatency = @($evaluations | Sort-Object elapsedMs | ForEach-Object { $_.elapsedMs })
$p95Latency = if ($sortedLatency.Count -gt 0) {
    $p95Index = [Math]::Min($sortedLatency.Count - 1, [Math]::Ceiling($sortedLatency.Count * 0.95) - 1)
    [long]$sortedLatency[$p95Index]
} else { 0 }

$typeGroups = $evaluations | Group-Object type | ForEach-Object {
    $items = @($_.Group)
    [pscustomobject]@{
        type = $_.Name
        count = $items.Count
        top1 = @($items | Where-Object { $_.top1Hit }).Count
        recallAt5 = @($items | Where-Object { $_.recallAt5 }).Count
        recallAt10 = @($items | Where-Object { $_.recallAt10 }).Count
        mrrAt10 = [Math]::Round((($items | Measure-Object -Property mrrAt10 -Average).Average), 4)
    }
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# 混合检索 Baseline 报告")
$lines.Add("")
$lines.Add("## 运行配置")
$lines.Add("")
$lines.Add("- 运行时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$lines.Add("- BaseUrl：``$BaseUrl``")
$lines.Add("- 接口：``GET /api/v1/search/hybrid``")
$lines.Add("- TopK：$TopK")
$lines.Add("- Benchmark：``$BenchmarkPath``")
$lines.Add("")
$lines.Add("## 指标汇总")
$lines.Add("")
$lines.Add("| 指标 | 值 |")
$lines.Add("| --- | ---: |")
$lines.Add("| 样例数 | $total |")
$lines.Add("| Top1 Evidence Hit | $top1 / $total |")
$lines.Add("| Recall@5 | $r5 / $total |")
$lines.Add("| Recall@10 | $r10 / $total |")
$lines.Add("| MRR@10 | $avgMrr |")
$lines.Add("| 平均耗时 | ${avgLatency}ms |")
$lines.Add("| P95耗时 | ${p95Latency}ms |")
$lines.Add("")
$lines.Add("> 说明：PR0 使用 ``requiredTerms`` 做弱自动命中判断，正式 Recall@K 需要后续人工标注 ``relevantChunkIds``。")
$lines.Add("")
$lines.Add("## 按类型汇总")
$lines.Add("")
$lines.Add("| 类型 | 数量 | Top1 | Recall@5 | Recall@10 | MRR@10 |")
$lines.Add("| --- | ---: | ---: | ---: | ---: | ---: |")
foreach ($group in $typeGroups) {
    $lines.Add("| $(Escape-MarkdownCell $group.type) | $($group.count) | $($group.top1) | $($group.recallAt5) | $($group.recallAt10) | $($group.mrrAt10) |")
}
$lines.Add("")
$lines.Add("## 样例明细")
$lines.Add("")
$lines.Add("| ID | 类型 | 问题 | 标准答案 | Top1 | R@5 | R@10 | 首个命中排名 | MRR | 耗时 |")
$lines.Add("| ---: | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |")
foreach ($item in $evaluations) {
    $firstRank = if ($null -ne $item.firstRelevantRank) { $item.firstRelevantRank } else { "-" }
    $lines.Add("| $($item.id) | $(Escape-MarkdownCell $item.type) | $(Escape-MarkdownCell $item.query) | $(Escape-MarkdownCell $item.groundTruthAnswer) | $($item.top1Hit) | $($item.recallAt5) | $($item.recallAt10) | $firstRank | $($item.mrrAt10) | $($item.elapsedMs)ms |")
}
$lines.Add("")
$lines.Add("## Top3 结果摘录")
$lines.Add("")
foreach ($item in $evaluations) {
    $lines.Add("### $($item.id). $($item.query)")
    $lines.Add("")
    if ($item.error) {
        $lines.Add("- 错误：$($item.error)")
        $lines.Add("")
        continue
    }
    $lines.Add("| Rank | Chunk | Score | Hit | Snippet |")
    $lines.Add("| ---: | ---: | ---: | --- | --- |")
    foreach ($result in @($item.topResults | Select-Object -First 3)) {
        $lines.Add("| $($result.rank) | $($result.chunkId) | $($result.score) | $($result.hit) | $(Escape-MarkdownCell $result.snippet) |")
    }
    $lines.Add("")
}
$lines.Add("## Baseline 结论")
$lines.Add("")
$lines.Add("1. 当前结果用于记录优化前状态，不代表最终业务验收结论。")
$lines.Add("2. 如果 Top10 有命中但 Top1/MRR 较低，说明主要问题是排序和证据前置不足。")
$lines.Add("3. 如果 Top10 无命中，说明当前召回阶段存在漏召回，需要通过 query expansion、双路召回或更大召回窗口改进。")
$lines.Add("4. 边界无答案问题需要结合最终回答链路评估，检索阶段召回相关词不等于可以回答。")

$reportDir = Split-Path $ReportPath -Parent
if ($reportDir -and -not (Test-Path $reportDir)) {
    New-Item -ItemType Directory $reportDir | Out-Null
}

$lines | Set-Content -Path $ReportPath -Encoding UTF8

Write-Host "Baseline report written to $ReportPath"
Write-Host "Top1=$top1/$total Recall@5=$r5/$total Recall@10=$r10/$total MRR@10=$avgMrr AvgLatency=${avgLatency}ms P95=${p95Latency}ms"
