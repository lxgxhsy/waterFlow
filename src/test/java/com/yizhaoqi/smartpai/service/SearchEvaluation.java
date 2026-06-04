package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.SmartPaiApplication;
import com.yizhaoqi.smartpai.entity.SearchResult;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Disabled("Manual search evaluation tool; run explicitly instead of as part of mvn test.")
public class SearchEvaluation {

    private static final int REPORT_TOP_K = 10;

    private final HybridSearchService searchService;

    SearchEvaluation() {
        this(new HybridSearchService());
    }

    SearchEvaluation(HybridSearchService searchService) {
        this.searchService = searchService;
    }

    record EvalCase(
            String category,
            String query,
            String expectedDocumentKey,
            List<String> expectedChunkKeys,
            List<String> expectedTextFragments,
            List<String> expectedSectionClues
    ) {
        EvalCase(String category,
                 String query,
                 String expectedDocumentKey,
                 List<String> expectedChunkKeys,
                 List<String> expectedTextFragments) {
            this(category, query, expectedDocumentKey, expectedChunkKeys, expectedTextFragments, List.of());
        }

        EvalCase(String query, List<String> expectedChunkKeys) {
            this("未分类", query, firstDocumentKey(expectedChunkKeys), expectedChunkKeys, List.of(), List.of());
        }
    }

    record RouteResults(List<SearchResult> bm25Results, List<SearchResult> vectorResults) {
    }

    @FunctionalInterface
    interface SearchResultsProvider {
        List<SearchResult> search(EvalCase evalCase);
    }

    record EvaluationVariant(String name, SearchResultsProvider provider) {
    }

    record VariantReport(String name, EvaluationReport report) {
    }

    record ComparisonReport(List<VariantReport> variantReports) {
    }

    record BenchmarkScenario(String name, SearchResultsProvider provider, int iterations) {
    }

    record BenchmarkResult(String name, int iterations, double averageMillis, double p95Millis) {
    }

    record BenchmarkReport(List<BenchmarkResult> results) {
    }

    record HitDetail(
            int rank,
            String chunkKey,
            String documentKey,
            String sectionTitle,
            String clauseNumber,
            double score,
            boolean expectedChunk,
            boolean expectedDocument,
            boolean expectedSection,
            List<String> matchedFragments
    ) {
    }

    record CaseReport(
            EvalCase evalCase,
            String expandedQuery,
            List<String> mergedChunkKeys,
            List<String> matchedTextFragments,
            Set<String> hitsAt10,
            List<HitDetail> hitDetails,
            double recallAt5,
            double recallAt10,
            double mrrAt10,
            double ndcgAt10,
            boolean documentHit,
            boolean sectionHit
    ) {
    }

    record EvaluationReport(
            List<CaseReport> caseReports,
            double averageRecallAt5,
            double averageRecallAt10,
            double averageMrrAt10,
            double averageNdcgAt10,
            double documentHitRate,
            double sectionHitRate
    ) {
    }

    public static void main(String[] args) {
        try (ConfigurableApplicationContext context = SpringApplication.run(SmartPaiApplication.class, args)) {
            SearchEvaluation evaluation = new SearchEvaluation(context.getBean(HybridSearchService.class));
            EvaluationReport report = evaluation.evaluateWithSearch(acceptanceCases(), REPORT_TOP_K);
            evaluation.printReport(report);
        }
    }

    static List<EvalCase> acceptanceCases() {
        return List.of(
                new EvalCase("原则类", "水库有哪些原则？", "prd93-principle",
                        List.of("prd93-principle:1"), List.of("防洪为主", "兼顾发电"), List.of("调度原则")),
                new EvalCase("原则类", "水库的调度原则有哪些？", "prd93-principle",
                        List.of("prd93-principle:1", "prd93-principle:2"), List.of("总控制原则", "分期控制原则"), List.of("总控制原则", "分期控制原则")),
                new EvalCase("水位类", "木瓜水库汛限水位是多少？", "prd93-water-level",
                        List.of("prd93-water-level:1"), List.of("307.00m", "310.00m"), List.of("汛限水位")),
                new EvalCase("水位类", "梅汛期限制水位是多少？", "prd93-water-level",
                        List.of("prd93-water-level:2"), List.of("梅汛期", "307.00m"), List.of("梅汛期")),
                new EvalCase("水位类", "台汛期限制水位是多少？", "prd93-water-level",
                        List.of("prd93-water-level:3"), List.of("台汛期", "310.00m"), List.of("台汛期")),
                new EvalCase("操作类", "非汛期水库怎么运行？", "prd93-operation",
                        List.of("prd93-operation:1"), List.of("非汛期", "兴利下限"), List.of("非汛期运行")),
                new EvalCase("操作类", "什么时候需要开闸放水？", "prd93-gate-operation",
                        List.of("prd93-gate-operation:1"), List.of("开闸", "放水", "泄洪"), List.of("闸门操作")),
                new EvalCase("位置类", "木瓜水库位于哪里？", "prd93-location",
                        List.of("prd93-location:1"), List.of("淳安县", "中洲镇"), List.of("工程位置")),
                new EvalCase("概况类", "木瓜水库工程概况是什么？", "prd93-overview",
                        List.of("prd93-overview:1"), List.of("小型水库", "控制运行计划"), List.of("工程概况")),
                new EvalCase("时间/汛期类", "汛期分为哪些阶段？", "prd93-flood-season",
                        List.of("prd93-flood-season:1"), List.of("梅汛期", "台汛期"), List.of("汛期")),
                new EvalCase("原则类", "水库兼有哪些功能？", "prd93-principle",
                        List.of("prd93-principle:1"), List.of("兼顾发电", "灌溉"), List.of("调度原则")),
                new EvalCase("概况类", "木瓜水库防洪保护对象有哪些？", "prd93-protection",
                        List.of("prd93-protection:1"), List.of("防洪保护对象", "行政村"), List.of("防洪保护对象"))
        );
    }

    EvaluationReport evaluate(List<EvalCase> cases,
                              int topK,
                              Function<EvalCase, RouteResults> routeResultsProvider) {
        List<CaseReport> reports = cases.stream()
                .map(evalCase -> {
                    RouteResults routeResults = routeResultsProvider.apply(evalCase);
                    List<SearchResult> mergedResults = searchService.mergeByRrf(
                            routeResults.bm25Results(),
                            routeResults.vectorResults(),
                            topK
                    );
                    return evaluateRankedResults(evalCase, mergedResults);
                })
                .toList();

        return reportFrom(reports);
    }

    EvaluationReport evaluateRanked(List<EvalCase> cases, SearchResultsProvider resultsProvider) {
        List<CaseReport> reports = cases.stream()
                .map(evalCase -> evaluateRankedResults(evalCase, resultsProvider.search(evalCase)))
                .toList();

        return reportFrom(reports);
    }

    EvaluationReport evaluateWithSearch(List<EvalCase> cases, int topK) {
        int resultK = Math.max(1, topK);
        List<CaseReport> reports = cases.stream()
                .map(evalCase -> evaluateRankedResults(evalCase, searchService.search(evalCase.query(), resultK)))
                .toList();

        return reportFrom(reports);
    }

    ComparisonReport compareVariants(List<EvalCase> cases, List<EvaluationVariant> variants) {
        return new ComparisonReport(variants.stream()
                .map(variant -> new VariantReport(variant.name(), evaluateRanked(cases, variant.provider())))
                .toList());
    }

    BenchmarkReport benchmark(List<EvalCase> cases, int warmupIterations, List<BenchmarkScenario> scenarios) {
        List<BenchmarkResult> results = scenarios.stream()
                .map(scenario -> benchmarkScenario(cases, warmupIterations, scenario))
                .toList();
        return new BenchmarkReport(results);
    }

    private CaseReport evaluateRankedResults(EvalCase evalCase, List<SearchResult> rankedResults) {
        String expandedQuery = searchService.expandQueryForRecall(evalCase.query());
        List<String> mergedChunkKeys = rankedResults.stream()
                .map(SearchEvaluation::chunkKey)
                .toList();
        List<String> matchedTextFragments = matchedTextFragments(rankedResults, evalCase.expectedTextFragments(), 10);
        Set<String> hitsAt10 = hits(mergedChunkKeys, evalCase.expectedChunkKeys(), 10);
        List<HitDetail> hitDetails = hitDetails(rankedResults, evalCase, 10);

        return new CaseReport(
                evalCase,
                expandedQuery,
                mergedChunkKeys,
                matchedTextFragments,
                hitsAt10,
                hitDetails,
                recallAtK(mergedChunkKeys, evalCase.expectedChunkKeys(), 5),
                recallAtK(mergedChunkKeys, evalCase.expectedChunkKeys(), 10),
                mrrAtK(mergedChunkKeys, evalCase.expectedChunkKeys(), 10),
                ndcgAtK(mergedChunkKeys, evalCase.expectedChunkKeys(), 10),
                hitDetails.stream().anyMatch(HitDetail::expectedDocument),
                hitDetails.stream().anyMatch(HitDetail::expectedSection)
        );
    }

    private EvaluationReport reportFrom(List<CaseReport> reports) {
        return new EvaluationReport(
                reports,
                average(reports.stream().map(CaseReport::recallAt5).toList()),
                average(reports.stream().map(CaseReport::recallAt10).toList()),
                average(reports.stream().map(CaseReport::mrrAt10).toList()),
                average(reports.stream().map(CaseReport::ndcgAt10).toList()),
                rate(reports.stream().map(CaseReport::documentHit).toList()),
                rate(reports.stream().map(CaseReport::sectionHit).toList())
        );
    }

    void printReport(EvaluationReport report) {
        System.out.println("Search Evaluation Report");
        System.out.println("========================");

        for (int i = 0; i < report.caseReports().size(); i++) {
            CaseReport caseReport = report.caseReports().get(i);
            System.out.printf(Locale.ROOT, "Case %02d [%s]: %s%n", i + 1, caseReport.evalCase().category(), caseReport.evalCase().query());
            System.out.printf(Locale.ROOT, "  expanded: %s%n", caseReport.expandedQuery());
            System.out.printf(Locale.ROOT, "  expectedDoc: %s%n", caseReport.evalCase().expectedDocumentKey());
            System.out.printf(Locale.ROOT, "  expectedChunks: %s%n", caseReport.evalCase().expectedChunkKeys());
            System.out.printf(Locale.ROOT, "  fragments: %s%n", caseReport.evalCase().expectedTextFragments());
            System.out.printf(Locale.ROOT, "  sectionClues: %s%n", caseReport.evalCase().expectedSectionClues());
            System.out.printf(Locale.ROOT, "  top10:    %s%n", caseReport.mergedChunkKeys());
            System.out.printf(Locale.ROOT, "  hits:     %s%n", caseReport.hitsAt10().isEmpty() ? "MISS" : caseReport.hitsAt10());
            System.out.printf(Locale.ROOT, "  text:     %s%n", caseReport.matchedTextFragments().isEmpty() ? "MISS" : caseReport.matchedTextFragments());
            System.out.printf(
                    Locale.ROOT,
                    "  Recall@5=%.3f  Recall@10=%.3f  MRR@10=%.3f  nDCG@10=%.3f  DocHit=%s  SectionHit=%s%n",
                    caseReport.recallAt5(),
                    caseReport.recallAt10(),
                    caseReport.mrrAt10(),
                    caseReport.ndcgAt10(),
                    caseReport.documentHit(),
                    caseReport.sectionHit()
            );
            caseReport.hitDetails().forEach(detail -> System.out.printf(
                    Locale.ROOT,
                    "    #%02d %s doc=%s section=%s clause=%s score=%.4f expectedChunk=%s expectedDoc=%s expectedSection=%s fragments=%s%n",
                    detail.rank(),
                    detail.chunkKey(),
                    detail.documentKey(),
                    detail.sectionTitle(),
                    detail.clauseNumber(),
                    detail.score(),
                    detail.expectedChunk(),
                    detail.expectedDocument(),
                    detail.expectedSection(),
                    detail.matchedFragments()
            ));
        }

        System.out.println("------------------------");
        System.out.printf(
                Locale.ROOT,
                "Average Recall@5=%.3f  Average Recall@10=%.3f  Average MRR@10=%.3f  Average nDCG@10=%.3f  DocHitRate=%.3f  SectionHitRate=%.3f%n",
                report.averageRecallAt5(),
                report.averageRecallAt10(),
                report.averageMrrAt10(),
                report.averageNdcgAt10(),
                report.documentHitRate(),
                report.sectionHitRate()
        );
    }

    void printComparisonReport(ComparisonReport comparisonReport) {
        System.out.println("Search Evaluation Comparison");
        System.out.println("============================");
        for (VariantReport variantReport : comparisonReport.variantReports()) {
            EvaluationReport report = variantReport.report();
            System.out.printf(
                    Locale.ROOT,
                    "%s: Recall@5=%.3f Recall@10=%.3f MRR@10=%.3f nDCG@10=%.3f DocHitRate=%.3f SectionHitRate=%.3f%n",
                    variantReport.name(),
                    report.averageRecallAt5(),
                    report.averageRecallAt10(),
                    report.averageMrrAt10(),
                    report.averageNdcgAt10(),
                    report.documentHitRate(),
                    report.sectionHitRate()
            );
        }
    }

    void printBenchmarkReport(BenchmarkReport benchmarkReport) {
        System.out.println("Search Performance Benchmark");
        System.out.println("============================");
        benchmarkReport.results().forEach(result -> System.out.printf(
                Locale.ROOT,
                "%s: iterations=%d avg=%.3fms p95=%.3fms%n",
                result.name(),
                result.iterations(),
                result.averageMillis(),
                result.p95Millis()
        ));
    }

    static double recallAtK(List<String> rankedChunkKeys, List<String> expectedChunkKeys, int k) {
        if (expectedChunkKeys.isEmpty()) {
            return 0.0d;
        }
        return (double) hits(rankedChunkKeys, expectedChunkKeys, k).size() / expectedChunkKeys.size();
    }

    static double mrrAtK(List<String> rankedChunkKeys, List<String> expectedChunkKeys, int k) {
        Set<String> expected = new LinkedHashSet<>(expectedChunkKeys);
        for (int i = 0; i < Math.min(k, rankedChunkKeys.size()); i++) {
            if (expected.contains(rankedChunkKeys.get(i))) {
                return 1.0d / (i + 1);
            }
        }
        return 0.0d;
    }

    static double ndcgAtK(List<String> rankedChunkKeys, List<String> expectedChunkKeys, int k) {
        if (expectedChunkKeys.isEmpty()) {
            return 0.0d;
        }
        Set<String> expected = new LinkedHashSet<>(expectedChunkKeys);
        double dcg = 0.0d;
        for (int i = 0; i < Math.min(k, rankedChunkKeys.size()); i++) {
            if (expected.contains(rankedChunkKeys.get(i))) {
                dcg += 1.0d / log2(i + 2);
            }
        }

        double idealDcg = 0.0d;
        for (int i = 0; i < Math.min(k, expected.size()); i++) {
            idealDcg += 1.0d / log2(i + 2);
        }
        return idealDcg == 0.0d ? 0.0d : dcg / idealDcg;
    }

    private BenchmarkResult benchmarkScenario(List<EvalCase> cases, int warmupIterations, BenchmarkScenario scenario) {
        for (int i = 0; i < warmupIterations; i++) {
            for (EvalCase evalCase : cases) {
                scenario.provider().search(evalCase);
            }
        }

        List<Double> millis = new ArrayList<>();
        for (int i = 0; i < scenario.iterations(); i++) {
            for (EvalCase evalCase : cases) {
                long start = System.nanoTime();
                scenario.provider().search(evalCase);
                millis.add((System.nanoTime() - start) / 1_000_000.0d);
            }
        }

        return new BenchmarkResult(
                scenario.name(),
                millis.size(),
                average(millis),
                percentile(millis, 0.95d)
        );
    }

    private static List<HitDetail> hitDetails(List<SearchResult> rankedResults, EvalCase evalCase, int k) {
        Set<String> expectedChunks = new LinkedHashSet<>(evalCase.expectedChunkKeys());
        List<HitDetail> details = new ArrayList<>();
        for (int i = 0; i < Math.min(k, rankedResults.size()); i++) {
            SearchResult result = rankedResults.get(i);
            String chunkKey = chunkKey(result);
            String documentKey = documentKey(result);
            List<String> matchedFragments = matchedTextFragments(List.of(result), evalCase.expectedTextFragments(), 1);
            boolean expectedSection = matchesAny(metadataText(result), evalCase.expectedSectionClues());
            details.add(new HitDetail(
                    i + 1,
                    chunkKey,
                    documentKey,
                    result.getSectionTitle(),
                    result.getClauseNumber(),
                    result.getScore() == null ? 0.0d : result.getScore(),
                    expectedChunks.contains(chunkKey),
                    evalCase.expectedDocumentKey() != null && evalCase.expectedDocumentKey().equals(documentKey),
                    expectedSection,
                    matchedFragments
            ));
        }
        return details;
    }

    private static List<String> matchedTextFragments(List<SearchResult> rankedResults, List<String> expectedFragments, int k) {
        if (expectedFragments.isEmpty()) {
            return List.of();
        }
        String topText = rankedResults.stream()
                .limit(k)
                .map(SearchResult::getTextContent)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));
        return expectedFragments.stream()
                .filter(topText::contains)
                .toList();
    }

    private static Set<String> hits(List<String> rankedChunkKeys, List<String> expectedChunkKeys, int k) {
        Set<String> expected = new LinkedHashSet<>(expectedChunkKeys);
        return rankedChunkKeys.stream()
                .limit(k)
                .filter(expected::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static double average(List<Double> values) {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0d);
    }

    private static double rate(List<Boolean> values) {
        if (values.isEmpty()) {
            return 0.0d;
        }
        long hits = values.stream().filter(Boolean::booleanValue).count();
        return (double) hits / values.size();
    }

    private static double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) {
            return 0.0d;
        }
        List<Double> sorted = values.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static boolean matchesAny(String text, List<String> fragments) {
        if (text == null || fragments == null || fragments.isEmpty()) {
            return false;
        }
        return fragments.stream().anyMatch(fragment -> !fragment.isBlank() && text.contains(fragment));
    }

    private static String metadataText(SearchResult result) {
        return List.of(
                        nullToEmpty(result.getSectionTitle()),
                        nullToEmpty(result.getClauseNumber()),
                        nullToEmpty(result.getTextContent())
                )
                .stream()
                .collect(Collectors.joining("\n"));
    }

    private static String chunkKey(SearchResult result) {
        return result.getFileMd5() + ":" + result.getChunkId();
    }

    private static String documentKey(SearchResult result) {
        if (result.getFileName() != null && !result.getFileName().isBlank()) {
            return result.getFileName();
        }
        return result.getFileMd5();
    }

    private static String firstDocumentKey(List<String> expectedChunkKeys) {
        if (expectedChunkKeys.isEmpty() || !expectedChunkKeys.get(0).contains(":")) {
            return "";
        }
        return expectedChunkKeys.get(0).substring(0, expectedChunkKeys.get(0).indexOf(':'));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0d);
    }

}
