package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.SearchResult;
import org.junit.jupiter.api.Disabled;

import java.util.ArrayList;
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

    record EvalCase(String query, List<String> expectedChunkKeys) {
    }

    record RouteResults(List<SearchResult> bm25Results, List<SearchResult> vectorResults) {
    }

    record CaseReport(
            EvalCase evalCase,
            String expandedQuery,
            List<String> mergedChunkKeys,
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
        SearchEvaluation evaluation = new SearchEvaluation();
        EvaluationReport report = evaluation.evaluate(acceptanceCases(), REPORT_TOP_K, SearchEvaluation::placeholderRouteResults);
        evaluation.printReport(report);
    }

    static List<EvalCase> acceptanceCases() {
        return List.of(
                new EvalCase("水库有哪些原则？", List.of("prd93-principle:1")),
                new EvalCase("水库的调度原则有哪些？", List.of("prd93-principle:1", "prd93-principle:2")),
                new EvalCase("木瓜水库汛限水位是多少？", List.of("prd93-water-level:1")),
                new EvalCase("梅汛期限制水位是多少？", List.of("prd93-water-level:2")),
                new EvalCase("台汛期限制水位是多少？", List.of("prd93-water-level:3")),
                new EvalCase("非汛期水库怎么运行？", List.of("prd93-operation:1")),
                new EvalCase("什么时候需要开闸放水？", List.of("prd93-gate-operation:1")),
                new EvalCase("水库以什么为主？", List.of("prd93-principle:3")),
                new EvalCase("水库兼有哪些功能？", List.of("prd93-principle:4")),
                new EvalCase("木瓜水库防洪保护对象有哪些？", List.of("prd93-protection:1"))
        );
    }

    EvaluationReport evaluate(List<EvalCase> cases,
                              int topK,
                              Function<EvalCase, RouteResults> routeResultsProvider) {
        List<CaseReport> reports = cases.stream()
                .map(evalCase -> evaluateCase(evalCase, topK, routeResultsProvider.apply(evalCase)))
                .toList();

        return new EvaluationReport(
                reports,
                average(reports.stream().map(CaseReport::recallAt5).toList()),
                average(reports.stream().map(CaseReport::recallAt10).toList()),
                average(reports.stream().map(CaseReport::mrrAt10).toList())
        );
    }

    private CaseReport evaluateCase(EvalCase evalCase, int topK, RouteResults routeResults) {
        String expandedQuery = searchService.expandQueryForRecall(evalCase.query());
        List<SearchResult> mergedResults = searchService.mergeByRrf(
                routeResults.bm25Results(),
                routeResults.vectorResults(),
                topK
        );
        List<String> mergedChunkKeys = mergedResults.stream()
                .map(SearchEvaluation::chunkKey)
                .toList();
        Set<String> hitsAt10 = hits(mergedChunkKeys, evalCase.expectedChunkKeys(), 10);

        return new CaseReport(
                evalCase,
                expandedQuery,
                mergedChunkKeys,
                hitsAt10,
                recallAtK(mergedChunkKeys, evalCase.expectedChunkKeys(), 5),
                recallAtK(mergedChunkKeys, evalCase.expectedChunkKeys(), 10),
                mrrAtK(mergedChunkKeys, evalCase.expectedChunkKeys(), 10)
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
            System.out.printf(Locale.ROOT, "  top10:    %s%n", caseReport.mergedChunkKeys());
            System.out.printf(Locale.ROOT, "  hits:     %s%n", caseReport.hitsAt10().isEmpty() ? "MISS" : caseReport.hitsAt10());
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

    private static RouteResults placeholderRouteResults(EvalCase evalCase) {
        List<SearchResult> bm25Results = new ArrayList<>();
        bm25Results.add(result("mock-bm25", 1, "BM25 placeholder distractor"));
        for (String expectedKey : evalCase.expectedChunkKeys()) {
            bm25Results.add(result(expectedKey, "Expected placeholder fragment for " + evalCase.query()));
        }

        List<SearchResult> vectorResults = new ArrayList<>();
        for (String expectedKey : evalCase.expectedChunkKeys()) {
            vectorResults.add(result(expectedKey, "Expected vector placeholder fragment for " + evalCase.query()));
        }
        vectorResults.add(result("mock-vector", 1, "Vector placeholder distractor"));

        return new RouteResults(bm25Results, vectorResults);
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

    private static SearchResult result(String chunkKey, String textContent) {
        int separator = chunkKey.lastIndexOf(':');
        if (separator < 1 || separator == chunkKey.length() - 1) {
            throw new IllegalArgumentException("chunkKey must use fileMd5:chunkId format: " + chunkKey);
        }
        return result(
                chunkKey.substring(0, separator),
                Integer.parseInt(chunkKey.substring(separator + 1)),
                textContent
        );
    }

    private static SearchResult result(String fileMd5, int chunkId, String textContent) {
        return new SearchResult(fileMd5, chunkId, textContent, 0.0d);
    }
}
