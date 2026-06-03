package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.SearchResult;
import org.junit.jupiter.api.Disabled;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Disabled("Manual search evaluation tool; run explicitly instead of as part of mvn test.")
public class SearchEvaluation {

    private static final int REPORT_TOP_K = 10;
    private static final List<SearchResult> PRD_ACCEPTANCE_FIXTURES = List.of(
            result("prd93-principle:1", "木瓜水库调度原则为防洪为主，兼顾发电、灌溉和兴利综合利用。"),
            result("prd93-principle:2", "运行原则包括总控制原则和分期控制原则，汛期按限制水位和预报雨情调度。"),
            result("prd93-water-level:1", "木瓜水库汛限水位按汛期分段控制，梅汛期限制水位为307.00m，台汛期限制水位为310.00m。"),
            result("prd93-water-level:2", "梅汛期水库限制水位为307.00m，超过控制水位时应及时预泄。"),
            result("prd93-water-level:3", "台汛期限制水位为310.00m，正常蓄水位314.00m，死水位298.00m。"),
            result("prd93-operation:1", "非汛期水库按兴利下限和正常蓄水位运行，兼顾供水、灌溉和发电。"),
            result("prd93-gate-operation:1", "接到大降雨或台风暴雨预报时，可通过开闸、放水、泄洪、预泄降低库水位。"),
            result("prd93-protection:1", "木瓜水库防洪保护对象包括下游行政村，需明确巡查责任人和联系方式。")
    );

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
        SearchEvaluation evaluation = new SearchEvaluation();
        EvaluationReport report = evaluation.evaluate(acceptanceCases(), REPORT_TOP_K, SearchEvaluation::fixtureRouteResults);
        evaluation.printReport(report);
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
        List<String> matchedTextFragments = matchedTextFragments(mergedResults, evalCase.expectedTextFragments(), 10);
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

    static RouteResults fixtureRouteResults(EvalCase evalCase) {
        List<SearchResult> expected = PRD_ACCEPTANCE_FIXTURES.stream()
                .filter(result -> evalCase.expectedChunkKeys().contains(chunkKey(result)))
                .toList();
        List<SearchResult> distractors = PRD_ACCEPTANCE_FIXTURES.stream()
                .filter(result -> !evalCase.expectedChunkKeys().contains(chunkKey(result)))
                .limit(2)
                .toList();

        List<SearchResult> bm25Results = new ArrayList<>(expected);
        bm25Results.addAll(distractors);

        List<SearchResult> vectorResults = reversedCopy(expected);
        vectorResults.addAll(reversedCopy(distractors));

        return new RouteResults(bm25Results, vectorResults);
    }

    private static List<SearchResult> reversedCopy(List<SearchResult> results) {
        List<SearchResult> copy = new ArrayList<>(results);
        Collections.reverse(copy);
        return copy;
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
