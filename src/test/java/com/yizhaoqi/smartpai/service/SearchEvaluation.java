package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.SmartPaiApplication;
import com.yizhaoqi.smartpai.entity.SearchResult;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

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

    record EvalCase(String query, List<String> expectedChunkKeys, List<String> expectedTextFragments) {
        EvalCase(String query, List<String> expectedChunkKeys) {
            this(query, expectedChunkKeys, List.of());
        }
    }

    record RouteResults(List<SearchResult> bm25Results, List<SearchResult> vectorResults) {
    }

    record CaseReport(
            EvalCase evalCase,
            String expandedQuery,
            List<String> mergedChunkKeys,
            List<String> matchedTextFragments,
            Set<String> hitsAt10,
            double recallAt5,
            double recallAt10,
            double mrrAt10
    ) {
    }

    record EvaluationReport(
            List<CaseReport> caseReports,
            double averageRecallAt5,
            double averageRecallAt10,
            double averageMrrAt10
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
                new EvalCase("水库有哪些原则？", List.of("prd93-principle:1"), List.of("防洪为主", "兼顾发电")),
                new EvalCase("水库的调度原则有哪些？", List.of("prd93-principle:1", "prd93-principle:2"), List.of("总控制原则", "分期控制原则")),
                new EvalCase("木瓜水库汛限水位是多少？", List.of("prd93-water-level:1"), List.of("307.00m", "310.00m")),
                new EvalCase("梅汛期限制水位是多少？", List.of("prd93-water-level:2"), List.of("梅汛期", "307.00m")),
                new EvalCase("台汛期限制水位是多少？", List.of("prd93-water-level:3"), List.of("台汛期", "310.00m")),
                new EvalCase("非汛期水库怎么运行？", List.of("prd93-operation:1"), List.of("非汛期", "兴利下限")),
                new EvalCase("什么时候需要开闸放水？", List.of("prd93-gate-operation:1"), List.of("开闸", "放水", "泄洪")),
                new EvalCase("水库以什么为主？", List.of("prd93-principle:1"), List.of("防洪为主")),
                new EvalCase("水库兼有哪些功能？", List.of("prd93-principle:1"), List.of("兼顾发电", "灌溉")),
                new EvalCase("木瓜水库防洪保护对象有哪些？", List.of("prd93-protection:1"), List.of("防洪保护对象", "行政村"))
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

    EvaluationReport evaluateWithSearch(List<EvalCase> cases, int topK) {
        int resultK = Math.max(1, topK);
        List<CaseReport> reports = cases.stream()
                .map(evalCase -> evaluateRankedResults(evalCase, searchService.search(evalCase.query(), resultK)))
                .toList();

        return reportFrom(reports);
    }

    private CaseReport evaluateRankedResults(EvalCase evalCase, List<SearchResult> rankedResults) {
        String expandedQuery = searchService.expandQueryForRecall(evalCase.query());
        List<String> mergedChunkKeys = rankedResults.stream()
                .map(SearchEvaluation::chunkKey)
                .toList();
        List<String> matchedTextFragments = matchedTextFragments(rankedResults, evalCase.expectedTextFragments(), 10);
        Set<String> hitsAt10 = hits(mergedChunkKeys, evalCase.expectedChunkKeys(), 10);

        return new CaseReport(
                evalCase,
                expandedQuery,
                mergedChunkKeys,
                matchedTextFragments,
                hitsAt10,
                recallAtK(mergedChunkKeys, evalCase.expectedChunkKeys(), 5),
                recallAtK(mergedChunkKeys, evalCase.expectedChunkKeys(), 10),
                mrrAtK(mergedChunkKeys, evalCase.expectedChunkKeys(), 10)
        );
    }

    private EvaluationReport reportFrom(List<CaseReport> reports) {
        return new EvaluationReport(
                reports,
                average(reports.stream().map(CaseReport::recallAt5).toList()),
                average(reports.stream().map(CaseReport::recallAt10).toList()),
                average(reports.stream().map(CaseReport::mrrAt10).toList())
        );
    }

    void printReport(EvaluationReport report) {
        System.out.println("Search Evaluation Report");
        System.out.println("========================");

        for (int i = 0; i < report.caseReports().size(); i++) {
            CaseReport caseReport = report.caseReports().get(i);
            System.out.printf(Locale.ROOT, "Case %02d: %s%n", i + 1, caseReport.evalCase().query());
            System.out.printf(Locale.ROOT, "  expanded: %s%n", caseReport.expandedQuery());
            System.out.printf(Locale.ROOT, "  expected: %s%n", caseReport.evalCase().expectedChunkKeys());
            System.out.printf(Locale.ROOT, "  fragments:%s%n", caseReport.evalCase().expectedTextFragments());
            System.out.printf(Locale.ROOT, "  top10:    %s%n", caseReport.mergedChunkKeys());
            System.out.printf(Locale.ROOT, "  hits:     %s%n", caseReport.hitsAt10().isEmpty() ? "MISS" : caseReport.hitsAt10());
            System.out.printf(Locale.ROOT, "  text:     %s%n", caseReport.matchedTextFragments().isEmpty() ? "MISS" : caseReport.matchedTextFragments());
            System.out.printf(
                    Locale.ROOT,
                    "  Recall@5=%.3f  Recall@10=%.3f  MRR@10=%.3f%n",
                    caseReport.recallAt5(),
                    caseReport.recallAt10(),
                    caseReport.mrrAt10()
            );
        }

        System.out.println("------------------------");
        System.out.printf(
                Locale.ROOT,
                "Average Recall@5=%.3f  Average Recall@10=%.3f  Average MRR@10=%.3f%n",
                report.averageRecallAt5(),
                report.averageRecallAt10(),
                report.averageMrrAt10()
        );
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

    private static String chunkKey(SearchResult result) {
        return result.getFileMd5() + ":" + result.getChunkId();
    }

}
