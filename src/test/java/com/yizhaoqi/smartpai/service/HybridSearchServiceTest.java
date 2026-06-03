package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HybridSearchServiceTest {

    private final HybridSearchService service = new HybridSearchService();

    @Test
    void expandsReservoirLocationQueryForRecall() {
        String expanded = service.expandQueryForRecall("水库位置");

        assertThat(expanded)
                .contains("水库位置")
                .contains("位于")
                .contains("所在")
                .contains("坝址")
                .contains("支流");
    }

    @Test
    void expandsWatershedQueryForRecall() {
        String expanded = service.expandQueryForRecall("水库流域情况");

        assertThat(expanded)
                .contains("水库流域情况")
                .contains("流域")
                .contains("支流")
                .contains("发源")
                .contains("注入")
                .contains("面积");
    }

    @Test
    void expandsPrincipleQueryForRecall() {
        String expanded = service.expandQueryForRecall("水库有哪些调度原则？");

        assertThat(expanded)
                .contains("水库有哪些调度原则？")
                .contains("调度原则")
                .contains("运行原则")
                .contains("控制原则")
                .contains("总控制原则")
                .contains("分期控制原则")
                .contains("防洪为主")
                .contains("兼顾发电")
                .contains("兴利")
                .contains("灌溉")
                .doesNotContain("开闸")
                .doesNotContain("放水")
                .doesNotContain("泄洪")
                .doesNotContain("预泄")
                .doesNotContain("放空管")
                .doesNotContain("电站满发")
                .doesNotContain("调蓄")
                .doesNotContain("降低水位")
                .doesNotContain("溢洪道")
                .doesNotContain("闸门");
    }

    @Test
    void expandsWaterLevelQueryForRecall() {
        String expanded = service.expandQueryForRecall("汛限水位是多少？");

        assertThat(expanded)
                .contains("汛限水位是多少？")
                .contains("汛限水位")
                .contains("限制水位")
                .contains("控制水位")
                .contains("库水位")
                .contains("梅汛期")
                .contains("台汛期")
                .contains("非汛期")
                .contains("正常蓄水位")
                .contains("死水位")
                .contains("307.00m")
                .contains("310.00m")
                .contains("314.00m")
                .contains("298.00m");
    }

    @Test
    void expandsOperationQueryForRecall() {
        String expanded = service.expandQueryForRecall("台汛期接到台风暴雨预报后如何调度？");

        assertThat(expanded)
                .contains("台汛期接到台风暴雨预报后如何调度？")
                .contains("开闸")
                .contains("放水")
                .contains("泄洪")
                .contains("预泄")
                .contains("调蓄")
                .contains("降低水位")
                .contains("溢洪道")
                .contains("闸门")
                .doesNotContain("放空管")
                .doesNotContain("电站满发");
    }

    @Test
    void doesNotTriggerOperationExpansionForGenericHandlingWords() {
        String expanded = service.expandQueryForRecall("水库出现问题怎么处理？");

        assertThat(expanded)
                .doesNotContain("开闸")
                .doesNotContain("放水")
                .doesNotContain("泄洪")
                .doesNotContain("预泄")
                .doesNotContain("放空管")
                .doesNotContain("电站满发")
                .doesNotContain("调蓄")
                .doesNotContain("降低水位")
                .doesNotContain("溢洪道")
                .doesNotContain("闸门");
    }

    @Test
    void expandsEntityDetailQueryForRecall() {
        String expanded = service.expandQueryForRecall("木瓜水库的巡查责任人是谁？联系方式？");

        assertThat(expanded)
                .contains("木瓜水库的巡查责任人是谁？联系方式？")
                .contains("防洪保护对象")
                .contains("行政村")
                .contains("巡查责任人")
                .contains("联系方式");
    }

    @Test
    void doesNotAddDomainExpansionWithoutTrigger() {
        String expanded = service.expandQueryForRecall("木瓜水库总库容是多少？");

        assertThat(expanded).isEqualTo("木瓜水库总库容是多少？");
    }

    @Test
    void mergesBm25AndVectorResultsByRrfRankAndStableChunkKey() {
        SearchResult bm25A = result("file-a", 1);
        SearchResult bm25B = result("file-b", 2);
        SearchResult bm25C = result("file-c", 3);
        SearchResult vectorB = result("file-b", 2);
        SearchResult vectorD = result("file-d", 4);
        SearchResult vectorA = result("file-a", 1);

        List<SearchResult> merged = service.mergeByRrf(
                List.of(bm25A, bm25B, bm25C),
                List.of(vectorB, vectorD, vectorA),
                4
        );

        assertThat(merged)
                .extracting(result -> result.getFileMd5() + ":" + result.getChunkId())
                .containsExactly("file-b:2", "file-a:1", "file-d:4", "file-c:3");
        assertThat(merged).hasSize(4);
        assertThat(merged.get(0).getScore()).isCloseTo(
                (1.0d / (60 + 1)) + (0.8d / (60 + 2)),
                within(1.0e-10)
        );
        assertThat(merged.get(0).getScore()).isGreaterThan(merged.get(1).getScore());
        assertThat(bm25B.getScore()).isEqualTo(0.0d);
        assertThat(vectorB.getScore()).isEqualTo(0.0d);
    }

    @Test
    void demoHybridRecallFlowForReservoirScheduling() {
        String expanded = service.expandQueryForRecall("台汛期水库如何调度并控制水位？");

        assertThat(expanded)
                .contains("台汛期水库如何调度并控制水位？")
                .contains("调度原则")
                .contains("运行原则")
                .contains("汛限水位")
                .contains("控制水位")
                .contains("台汛期")
                .contains("防洪为主");

        SearchResult bm25WaterLevel = result("reservoir-plan", 1);
        SearchResult bm25GateOperation = result("reservoir-plan", 2);
        SearchResult bm25Duplicate = result("reservoir-plan", 1);
        SearchResult vectorGateOperation = result("reservoir-plan", 2);
        SearchResult vectorForecast = result("flood-control", 3);
        SearchResult vectorWaterLevel = result("reservoir-plan", 1);

        List<SearchResult> merged = service.mergeByRrf(
                List.of(bm25WaterLevel, bm25GateOperation, bm25Duplicate),
                List.of(vectorGateOperation, vectorForecast, vectorWaterLevel),
                3
        );

        assertThat(merged)
                .hasSize(3)
                .extracting(result -> result.getFileMd5() + ":" + result.getChunkId())
                .containsExactly("reservoir-plan:2", "reservoir-plan:1", "flood-control:3");
        assertThat(merged)
                .extracting(SearchResult::getScore)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void rrfMergeUsesFirstRankOnlyForDuplicateWithinSameRoute() {
        SearchResult first = result("file-a", 1);
        SearchResult duplicate = result("file-a", 1);
        SearchResult second = result("file-b", 2);

        List<SearchResult> merged = service.mergeByRrf(
                List.of(first, duplicate, second),
                List.of(),
                10
        );

        assertThat(merged)
                .extracting(result -> result.getFileMd5() + ":" + result.getChunkId())
                .containsExactly("file-a:1", "file-b:2");
    }

    @Test
    void rrfMergeTruncatesToTopK() {
        List<SearchResult> merged = service.mergeByRrf(
                List.of(result("file-a", 1), result("file-b", 2), result("file-c", 3)),
                List.of(result("file-d", 4)),
                2
        );

        assertThat(merged)
                .hasSize(2)
                .extracting(result -> result.getFileMd5() + ":" + result.getChunkId())
                .containsExactly("file-d:4", "file-a:1");
        assertThat(merged.get(0).getScore()).isGreaterThan(merged.get(1).getScore());
    }

    @Test
    void rrfMergeReturnsEmptyForEmptyInputs() {
        List<SearchResult> merged = service.mergeByRrf(List.of(), List.of(), 10);

        assertThat(merged).isEmpty();
    }

    @Test
    void searchEvaluationFrameworkRunsWithMockRoutesWithoutEs() {
        SearchEvaluation evaluation = new SearchEvaluation(service);
        SearchEvaluation.EvalCase evalCase = new SearchEvaluation.EvalCase(
                "水库的调度原则有哪些？",
                List.of("eval-principle:1", "eval-principle:2")
        );

        SearchEvaluation.EvaluationReport report = evaluation.evaluate(
                List.of(evalCase),
                10,
                ignored -> new SearchEvaluation.RouteResults(
                        List.of(
                                result("eval-principle", 1),
                                result("distractor-bm25", 10)
                        ),
                        List.of(
                                result("distractor-vector", 20),
                                result("eval-principle", 2)
                        )
                )
        );

        assertThat(report.caseReports()).hasSize(1);
        SearchEvaluation.CaseReport caseReport = report.caseReports().get(0);

        assertThat(caseReport.expandedQuery())
                .contains("水库的调度原则有哪些？")
                .contains("调度原则")
                .contains("防洪为主");
        assertThat(caseReport.mergedChunkKeys())
                .contains("eval-principle:1", "eval-principle:2");
        assertThat(caseReport.hitsAt10())
                .containsExactlyInAnyOrder("eval-principle:1", "eval-principle:2");
        assertThat(caseReport.recallAt5()).isEqualTo(1.0d);
        assertThat(caseReport.recallAt10()).isEqualTo(1.0d);
        assertThat(caseReport.mrrAt10()).isGreaterThan(0.0d);
        assertThat(report.averageRecallAt5()).isEqualTo(1.0d);
        assertThat(report.averageRecallAt10()).isEqualTo(1.0d);
        assertThat(report.averageMrrAt10()).isEqualTo(caseReport.mrrAt10());
    }

    @Test
    void rrfRankStartsAtOne() {
        List<SearchResult> merged = service.mergeByRrf(
                List.of(result("file-a", 1)),
                List.of(),
                10
        );

        assertThat(merged)
                .singleElement()
                .extracting(SearchResult::getScore)
                .isEqualTo(0.8d / (60 + 1));
    }

    private SearchResult result(String fileMd5, int chunkId) {
        return new SearchResult(fileMd5, chunkId, "content-" + chunkId, 0.0d);
    }
}
