package com.yizhaoqi.smartpai.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.yizhaoqi.smartpai.config.AiProperties;
import com.yizhaoqi.smartpai.entity.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    void anonymousPermissionQueryLimitsResultsToPublicDocuments() {
        Query permissionQuery = service.buildPermissionQuery(null, List.of());

        assertThat(permissionQuery.isTerm()).isTrue();
        assertThat(permissionQuery.term().field()).isEqualTo("isPublic");
        assertThat(permissionQuery.term().value().booleanValue()).isTrue();
    }

    @Test
    void bm25AndVectorRecallRequestsCarrySamePermissionFilter() {
        SearchRequest bm25Request = service.buildBm25SearchRequest("汛限水位", "42", List.of("FLOOD", "OPS"), 10);
        SearchRequest vectorRequest = service.buildVectorSearchRequest(List.of(0.1f, 0.2f, 0.3f), "42", List.of("FLOOD", "OPS"), 10);

        Query bm25PermissionFilter = bm25Request.query().bool().filter().get(0);
        Query vectorPermissionFilter = vectorRequest.knn().get(0).filter().get(0);

        assertPermissionFilterContainsUserPublicAndOrgTags(bm25PermissionFilter);
        assertPermissionFilterContainsUserPublicAndOrgTags(vectorPermissionFilter);
    }

    @Test
    void anonymousBm25AndVectorRecallRequestsUsePublicPermissionFilter() {
        SearchRequest bm25Request = service.buildBm25SearchRequest("公开规程", null, List.of(), 10);
        SearchRequest vectorRequest = service.buildVectorSearchRequest(List.of(0.1f, 0.2f, 0.3f), null, List.of(), 10);

        assertPublicOnlyFilter(bm25Request.query().bool().filter().get(0));
        assertPublicOnlyFilter(vectorRequest.knn().get(0).filter().get(0));
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
    void rerankerExtensionPointCanReorderMergedCandidates() {
        HybridSearchService rerankingService = new HybridSearchService();
        rerankingService.setAiProperties(rerankerProperties(true, 50));
        rerankingService.setSearchReranker((query, candidates, topK) -> {
            List<SearchResult> reversed = new ArrayList<>(candidates);
            Collections.reverse(reversed);
            return reversed;
        });

        List<SearchResult> merged = rerankingService.mergeByRrf(
                List.of(result("file-a", 1), result("file-b", 2)),
                List.of(),
                10
        );
        List<SearchResult> reranked = rerankingService.applyReranker("query", merged, 10);

        assertThat(reranked)
                .extracting(result -> result.getFileMd5() + ":" + result.getChunkId())
                .containsExactly("file-b:2", "file-a:1");
    }

    @Test
    void rerankerDisabledByDefaultDoesNotCallConfiguredReranker() {
        HybridSearchService defaultService = new HybridSearchService();
        defaultService.setSearchReranker((query, candidates, topK) -> {
            throw new AssertionError("reranker should not be called when disabled");
        });
        List<SearchResult> merged = defaultService.mergeByRrf(
                List.of(result("file-a", 1), result("file-b", 2)),
                List.of(),
                10
        );

        List<SearchResult> results = defaultService.applyReranker("query", merged, 1);

        assertThat(results)
                .singleElement()
                .extracting(result -> result.getFileMd5() + ":" + result.getChunkId())
                .isEqualTo("file-a:1");
    }

    @Test
    void enabledRerankerReceivesDefaultTop50CandidatesAndSearchTruncatesFinalTopK() {
        InMemoryLargeRecallHybridSearchService searchService = new InMemoryLargeRecallHybridSearchService(60);
        searchService.setAiProperties(rerankerProperties(true, 50));
        AtomicInteger receivedCandidates = new AtomicInteger();
        AtomicInteger receivedTopK = new AtomicInteger();
        searchService.setSearchReranker((query, candidates, topK) -> {
            receivedCandidates.set(candidates.size());
            receivedTopK.set(topK);
            List<SearchResult> reversed = new ArrayList<>(candidates);
            Collections.reverse(reversed);
            return reversed;
        });

        List<SearchResult> results = searchService.search("query", 5);

        assertThat(receivedCandidates).hasValue(50);
        assertThat(receivedTopK).hasValue(5);
        assertThat(results).hasSize(5);
        assertThat(results)
                .extracting(result -> result.getFileMd5() + ":" + result.getChunkId())
                .containsExactly("file-49:49", "file-48:48", "file-47:47", "file-46:46", "file-45:45");
    }

    @Test
    void enabledRerankerUsesConfiguredCandidateLimit() {
        InMemoryLargeRecallHybridSearchService searchService = new InMemoryLargeRecallHybridSearchService(60);
        searchService.setAiProperties(rerankerProperties(true, 12));
        AtomicInteger receivedCandidates = new AtomicInteger();
        searchService.setSearchReranker((query, candidates, topK) -> {
            receivedCandidates.set(candidates.size());
            return candidates;
        });

        List<SearchResult> results = searchService.search("query", 5);

        assertThat(receivedCandidates).hasValue(12);
        assertThat(results).hasSize(5);
    }

    @Test
    void rerankerScoreIsReturnedWhenRerankerSucceeds() {
        HybridSearchService rerankingService = new HybridSearchService();
        rerankingService.setAiProperties(rerankerProperties(true, 50));
        rerankingService.setSearchReranker((query, candidates, topK) -> List.of(
                new SearchResult(
                        candidates.get(1).getFileMd5(),
                        candidates.get(1).getChunkId(),
                        candidates.get(1).getTextContent(),
                        9.75d
                ),
                new SearchResult(
                        candidates.get(0).getFileMd5(),
                        candidates.get(0).getChunkId(),
                        candidates.get(0).getTextContent(),
                        8.25d
                )
        ));
        List<SearchResult> merged = rerankingService.mergeByRrf(
                List.of(result("file-a", 1), result("file-b", 2)),
                List.of(),
                10
        );

        List<SearchResult> results = rerankingService.applyReranker("query", merged, 2);

        assertThat(results)
                .extracting(result -> result.getFileMd5() + ":" + result.getChunkId())
                .containsExactly("file-b:2", "file-a:1");
        assertThat(results)
                .extracting(SearchResult::getScore)
                .containsExactly(9.75d, 8.25d);
    }

    @Test
    void rerankerFailureFallsBackToRrfScoresAndOrdering() {
        HybridSearchService rerankingService = new HybridSearchService();
        rerankingService.setAiProperties(rerankerProperties(true, 50));
        rerankingService.setSearchReranker((query, candidates, topK) -> {
            throw new RuntimeException("reranker unavailable");
        });
        List<SearchResult> merged = rerankingService.mergeByRrf(
                List.of(result("file-a", 1), result("file-b", 2)),
                List.of(),
                10
        );

        List<SearchResult> results = rerankingService.applyReranker("query", merged, 1);

        assertThat(results)
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getFileMd5()).isEqualTo("file-a");
                    assertThat(result.getScore()).isEqualTo(0.8d / (60 + 1));
                });
    }

    @Test
    void nullRerankerResultFallsBackToRrfScoresAndOrdering() {
        HybridSearchService rerankingService = new HybridSearchService();
        rerankingService.setAiProperties(rerankerProperties(true, 50));
        rerankingService.setSearchReranker((query, candidates, topK) -> null);
        List<SearchResult> merged = rerankingService.mergeByRrf(
                List.of(result("file-a", 1), result("file-b", 2)),
                List.of(),
                10
        );

        List<SearchResult> results = rerankingService.applyReranker("query", merged, 1);

        assertThat(results)
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getFileMd5()).isEqualTo("file-a");
                    assertThat(result.getScore()).isEqualTo(0.8d / (60 + 1));
                });
    }

    @Test
    void enabledRerankerWithoutImplementationFallsBackToRrfScoresAndOrdering() {
        HybridSearchService rerankingService = new HybridSearchService();
        rerankingService.setAiProperties(rerankerProperties(true, 50));
        List<SearchResult> merged = rerankingService.mergeByRrf(
                List.of(result("file-a", 1), result("file-b", 2)),
                List.of(),
                10
        );

        List<SearchResult> results = rerankingService.applyReranker("query", merged, 1);

        assertThat(results)
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.getFileMd5()).isEqualTo("file-a");
                    assertThat(result.getScore()).isEqualTo(0.8d / (60 + 1));
                });
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
        assertThat(caseReport.matchedTextFragments()).isEmpty();
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

    @Test
    void prdAcceptanceEvaluationRunsThroughHybridSearchRecallFlow() {
        InMemoryRecallHybridSearchService recallService = new InMemoryRecallHybridSearchService();
        SearchEvaluation evaluation = new SearchEvaluation(recallService);

        SearchEvaluation.EvaluationReport report = evaluation.evaluateWithSearch(
                SearchEvaluation.acceptanceCases(),
                10
        );

        assertThat(report.caseReports()).hasSize(10);
        assertThat(report.caseReports())
                .allSatisfy(caseReport -> {
                    assertThat(caseReport.recallAt10()).isEqualTo(1.0d);
                    assertThat(caseReport.matchedTextFragments())
                            .containsExactlyInAnyOrderElementsOf(caseReport.evalCase().expectedTextFragments());
                });
        assertThat(report.caseReports())
                .filteredOn(caseReport -> caseReport.evalCase().query().contains("汛限水位"))
                .singleElement()
                .satisfies(caseReport -> assertThat(caseReport.matchedTextFragments())
                        .contains("307.00m", "310.00m"));
        assertThat(report.caseReports())
                .filteredOn(caseReport -> caseReport.evalCase().query().contains("开闸放水"))
                .singleElement()
                .satisfies(caseReport -> assertThat(caseReport.matchedTextFragments())
                        .contains("开闸", "放水", "泄洪"));
        assertThat(recallService.bm25RecallQueries())
                .hasSize(10)
                .anySatisfy(query -> assertThat(query).contains("307.00m", "310.00m"))
                .anySatisfy(query -> assertThat(query).contains("开闸", "放水", "泄洪"));
        assertThat(recallService.vectorRecallCount()).isEqualTo(10);
    }

    @Test
    void expandsHitChunkWithAdjacentAndParentChunksForAnswerContext() {
        HybridSearchService expansionService = new InMemoryContextExpansionHybridSearchService(List.of(
                richResult("manual", 1, "前一块", "42", null, false, null),
                richResult("manual", 2, "命中块", "42", null, false, 10),
                richResult("manual", 3, "后一块", "42", null, false, null),
                richResult("manual", 10, "父块标题", "42", null, false, null)
        ));
        SearchResult hit = richResult("manual", 2, "命中块", "42", null, false, 10);
        hit.setFileName("调度规程.pdf");

        expansionService.expandResultContexts(List.of(hit), "42", List.of());

        assertThat(hit.getContextChunks())
                .extracting(SearchResult.ContextChunk::getChunkId)
                .containsExactly(2, 1, 3, 10);
        assertThat(hit.getContextChunks())
                .extracting(SearchResult.ContextChunk::getRelation)
                .containsExactly("hit", "adjacent", "adjacent", "parent");
        assertThat(hit.getContextChunks().get(0).getFileName()).isEqualTo("调度规程.pdf");
    }

    @Test
    void contextExpansionDoesNotBypassPermissionFiltering() {
        HybridSearchService expansionService = new InMemoryContextExpansionHybridSearchService(List.of(
                richResult("manual", 1, "其他用户前一块", "99", null, false, null),
                richResult("manual", 2, "命中块", "42", null, false, null),
                richResult("manual", 3, "同用户后一块", "42", null, false, null),
                richResult("manual", 4, "公开块", null, null, true, null),
                richResult("manual", 5, "同组织块", "88", "OPS", false, null)
        ));
        SearchResult hit = richResult("manual", 2, "命中块", "42", null, false, null);

        expansionService.expandResultContexts(List.of(hit), "42", List.of("OPS"));

        assertThat(hit.getContextChunks())
                .extracting(SearchResult.ContextChunk::getChunkId)
                .containsExactly(2, 3);
        assertThat(hit.getContextChunks())
                .extracting(SearchResult.ContextChunk::getTextContent)
                .doesNotContain("其他用户前一块");
    }

    @Test
    void contextExpansionDoesNotChangeTopKOrderingOrScores() {
        InMemoryContextExpansionHybridSearchService searchService = new InMemoryContextExpansionHybridSearchService(List.of(
                result("file-a", 1, "file-a before"),
                result("file-a", 2, "file-a hit"),
                result("file-b", 1, "file-b hit"),
                result("file-b", 2, "file-b after")
        ));
        searchService.setRecallResults(
                List.of(result("file-a", 2, "file-a hit"), result("file-b", 1, "file-b hit")),
                List.of()
        );

        List<SearchResult> results = searchService.search("query", 2);

        assertThat(results)
                .extracting(result -> result.getFileMd5() + ":" + result.getChunkId())
                .containsExactly("file-a:2", "file-b:1");
        assertThat(results)
                .extracting(SearchResult::getScore)
                .containsExactly(0.8d / (60 + 1), 0.8d / (60 + 2));
        assertThat(results)
                .allSatisfy(result -> assertThat(result.getContextChunks()).isNotEmpty());
    }

    @Test
    void disabledContextExpansionLeavesResultsAsCurrentBehavior() {
        InMemoryContextExpansionHybridSearchService expansionService = new InMemoryContextExpansionHybridSearchService(List.of(
                result("manual", 1, "前一块"),
                result("manual", 2, "命中块"),
                result("manual", 3, "后一块")
        ));
        expansionService.setAiProperties(contextExpansionProperties(false, 1, 1));
        SearchResult hit = result("manual", 2, "命中块");

        expansionService.expandResultContexts(List.of(hit), null, List.of());

        assertThat(hit.getContextChunks()).isEmpty();
        assertThat(expansionService.contextLookupCount()).isZero();
    }

    private SearchResult result(String fileMd5, int chunkId) {
        return new SearchResult(fileMd5, chunkId, "content-" + chunkId, 0.0d);
    }

    private static AiProperties rerankerProperties(boolean enabled, int candidateLimit) {
        AiProperties properties = new AiProperties();
        properties.getReranker().setEnabled(enabled);
        properties.getReranker().setCandidateLimit(candidateLimit);
        properties.getContextExpansion().setEnabled(false);
        return properties;
    }

    private static AiProperties contextExpansionProperties(boolean enabled, int beforeWindow, int afterWindow) {
        AiProperties properties = new AiProperties();
        properties.getContextExpansion().setEnabled(enabled);
        properties.getContextExpansion().setBeforeWindow(beforeWindow);
        properties.getContextExpansion().setAfterWindow(afterWindow);
        return properties;
    }

    private static SearchResult result(String fileMd5, int chunkId, String textContent) {
        return new SearchResult(fileMd5, chunkId, textContent, 0.0d, null, null, true);
    }

    private static SearchResult richResult(String fileMd5,
                                           int chunkId,
                                           String textContent,
                                           String userId,
                                           String orgTag,
                                           boolean isPublic,
                                           Integer parentChunkId) {
        SearchResult result = new SearchResult(fileMd5, chunkId, textContent, 0.0d, userId, orgTag, isPublic);
        result.setParentChunkId(parentChunkId);
        result.setSectionTitle("第" + chunkId + "节");
        result.setPageNumber(chunkId);
        result.setClauseNumber("T-" + chunkId);
        return result;
    }

    private static class InMemoryRecallHybridSearchService extends HybridSearchService {

        private final List<SearchResult> corpus = List.of(
                result("prd93-principle", 1, "木瓜水库调度原则为防洪为主，兼顾发电、灌溉和兴利综合利用。"),
                result("prd93-principle", 2, "运行原则包括总控制原则和分期控制原则，汛期按限制水位和预报雨情调度。"),
                result("prd93-water-level", 1, "木瓜水库汛限水位按汛期分段控制，梅汛期限制水位为307.00m，台汛期限制水位为310.00m。"),
                result("prd93-water-level", 2, "梅汛期水库限制水位为307.00m，超过控制水位时应及时预泄。"),
                result("prd93-water-level", 3, "台汛期限制水位为310.00m，正常蓄水位314.00m，死水位298.00m。"),
                result("prd93-operation", 1, "非汛期水库按兴利下限和正常蓄水位运行，兼顾供水、灌溉和发电。"),
                result("prd93-gate-operation", 1, "接到大降雨或台风暴雨预报时，可通过开闸、放水、泄洪、预泄降低库水位。"),
                result("prd93-protection", 1, "木瓜水库防洪保护对象包括下游行政村，需明确巡查责任人和联系方式。"),
                result("distractor-geology", 1, "坝址地质条件、岩性和渗流监测记录用于工程安全复核。"),
                result("distractor-weather", 1, "气象站记录包括风速、湿度和逐小时降雨量观测数据。"),
                result("distractor-power", 1, "电站机组检修计划按年度维护窗口编制。"),
                result("distractor-admin", 1, "档案归档要求包括目录、页码和移交签收记录。")
        );
        private final List<String> bm25RecallQueries = new ArrayList<>();
        private int vectorRecallCount;
        private volatile String embeddedQuery;

        private InMemoryRecallHybridSearchService() {
            setAiProperties(contextExpansionProperties(false, 1, 1));
        }

        @Override
        protected List<Float> embedToVectorList(String text) {
            embeddedQuery = text;
            return List.of(1.0f, 0.0f, 0.0f);
        }

        @Override
        protected List<SearchResult> searchBm25WithPermission(String recallQuery,
                                                              String userDbId,
                                                              List<String> userEffectiveTags,
                                                              int recallK) {
            bm25RecallQueries.add(recallQuery);
            List<String> terms = List.of(recallQuery.split("\\s+"));
            return corpus.stream()
                    .map(result -> new ScoredResult(result, lexicalScore(terms, result.getTextContent())))
                    .filter(scored -> scored.score() > 0)
                    .sorted(Comparator.comparingInt(ScoredResult::score).reversed())
                    .limit(recallK)
                    .map(scored -> scored.result())
                    .toList();
        }

        @Override
        protected List<SearchResult> searchVectorWithPermission(List<Float> queryVector,
                                                                String userDbId,
                                                                List<String> userEffectiveTags,
                                                                int recallK) {
            vectorRecallCount++;
            return semanticCandidates(embeddedQuery).stream()
                    .limit(recallK)
                    .toList();
        }

        @Override
        protected void attachFileNames(List<SearchResult> results) {
            // The evaluation corpus is in-memory and does not require file metadata lookups.
        }

        private int lexicalScore(List<String> terms, String text) {
            int score = 0;
            for (String term : terms) {
                if (!term.isBlank() && text.contains(term)) {
                    score++;
                }
            }
            return score;
        }

        private List<SearchResult> semanticCandidates(String query) {
            if (query.contains("原则")) {
                return byChunkKeys("prd93-principle:1", "prd93-principle:2", "prd93-water-level:1");
            }
            if (query.contains("汛限水位") || query.contains("限制水位") || query.contains("梅汛期") || query.contains("台汛期")) {
                return byChunkKeys("prd93-water-level:1", "prd93-water-level:2", "prd93-water-level:3");
            }
            if (query.contains("非汛期")) {
                return byChunkKeys("prd93-operation:1", "prd93-water-level:3", "prd93-principle:1");
            }
            if (query.contains("开闸") || query.contains("放水")) {
                return byChunkKeys("prd93-gate-operation:1", "prd93-water-level:2", "prd93-water-level:1");
            }
            if (query.contains("为主") || query.contains("功能")) {
                return byChunkKeys("prd93-principle:1", "prd93-operation:1", "prd93-principle:2");
            }
            if (query.contains("保护对象")) {
                return byChunkKeys("prd93-protection:1", "prd93-principle:1", "distractor-admin:1");
            }
            return byChunkKeys("distractor-geology:1", "distractor-weather:1");
        }

        private List<SearchResult> byChunkKeys(String... chunkKeys) {
            List<String> keys = List.of(chunkKeys);
            return corpus.stream()
                    .filter(result -> keys.contains(result.getFileMd5() + ":" + result.getChunkId()))
                    .sorted(Comparator.comparingInt(result -> keys.indexOf(result.getFileMd5() + ":" + result.getChunkId())))
                    .toList();
        }

        private List<String> bm25RecallQueries() {
            return bm25RecallQueries;
        }

        private int vectorRecallCount() {
            return vectorRecallCount;
        }

        private record ScoredResult(SearchResult result, int score) {
        }
    }

    private static class InMemoryLargeRecallHybridSearchService extends HybridSearchService {

        private final List<SearchResult> corpus;
        private final AtomicReference<String> embeddedQuery = new AtomicReference<>();

        private InMemoryLargeRecallHybridSearchService(int corpusSize) {
            List<SearchResult> results = new ArrayList<>();
            for (int i = 0; i < corpusSize; i++) {
                results.add(result("file-" + i, i, "content-" + i));
            }
            this.corpus = results;
            setAiProperties(contextExpansionProperties(false, 1, 1));
        }

        @Override
        protected List<Float> embedToVectorList(String text) {
            embeddedQuery.set(text);
            return List.of(1.0f, 0.0f, 0.0f);
        }

        @Override
        protected List<SearchResult> searchBm25WithPermission(String recallQuery,
                                                              String userDbId,
                                                              List<String> userEffectiveTags,
                                                              int recallK) {
            return corpus.stream()
                    .limit(recallK)
                    .toList();
        }

        @Override
        protected List<SearchResult> searchVectorWithPermission(List<Float> queryVector,
                                                                String userDbId,
                                                                List<String> userEffectiveTags,
                                                                int recallK) {
            assertThat(embeddedQuery.get()).isEqualTo("query");
            return List.of();
        }

        @Override
        protected void attachFileNames(List<SearchResult> results) {
            // In-memory test data has no file metadata.
        }
    }

    private static class InMemoryContextExpansionHybridSearchService extends HybridSearchService {

        private final List<SearchResult> corpus;
        private List<SearchResult> bm25Results = List.of();
        private List<SearchResult> vectorResults = List.of();
        private int contextLookupCount;

        private InMemoryContextExpansionHybridSearchService(List<SearchResult> corpus) {
            this.corpus = corpus;
            setAiProperties(contextExpansionProperties(true, 1, 1));
        }

        private void setRecallResults(List<SearchResult> bm25Results, List<SearchResult> vectorResults) {
            this.bm25Results = bm25Results;
            this.vectorResults = vectorResults;
        }

        private int contextLookupCount() {
            return contextLookupCount;
        }

        @Override
        protected List<Float> embedToVectorList(String text) {
            return List.of(1.0f, 0.0f, 0.0f);
        }

        @Override
        protected List<SearchResult> searchBm25WithPermission(String recallQuery,
                                                              String userDbId,
                                                              List<String> userEffectiveTags,
                                                              int recallK) {
            return bm25Results.stream().limit(recallK).toList();
        }

        @Override
        protected List<SearchResult> searchVectorWithPermission(List<Float> queryVector,
                                                                String userDbId,
                                                                List<String> userEffectiveTags,
                                                                int recallK) {
            return vectorResults.stream().limit(recallK).toList();
        }

        @Override
        protected List<SearchResult> searchContextChunksWithPermission(SearchResult hit,
                                                                       String userDbId,
                                                                       List<String> userEffectiveTags) {
            contextLookupCount++;
            return corpus.stream()
                    .filter(candidate -> candidate.getFileMd5().equals(hit.getFileMd5()))
                    .filter(candidate -> permitted(candidate, userDbId, userEffectiveTags))
                    .filter(candidate -> isContextCandidate(hit, candidate))
                    .toList();
        }

        @Override
        protected void attachFileNames(List<SearchResult> results) {
            // In-memory test data has no file metadata repository.
        }

        private boolean permitted(SearchResult candidate, String userDbId, List<String> userEffectiveTags) {
            if (userDbId == null && (userEffectiveTags == null || userEffectiveTags.isEmpty())) {
                return Boolean.TRUE.equals(candidate.getIsPublic());
            }
            return Boolean.TRUE.equals(candidate.getIsPublic())
                    || userDbId != null && userDbId.equals(candidate.getUserId())
                    || candidate.getOrgTag() != null
                    && userEffectiveTags != null
                    && userEffectiveTags.contains(candidate.getOrgTag());
        }

        private boolean isContextCandidate(SearchResult hit, SearchResult candidate) {
            int chunkId = candidate.getChunkId();
            int hitChunkId = hit.getChunkId();
            return Math.abs(chunkId - hitChunkId) <= 1
                    || hit.getParentChunkId() != null && hit.getParentChunkId().equals(chunkId);
        }
    }

    private void assertPermissionFilterContainsUserPublicAndOrgTags(Query permissionFilter) {
        List<String> terms = flattenTerms(permissionFilter);

        assertThat(terms)
                .contains("userId=42")
                .contains("isPublic=true")
                .contains("orgTag=FLOOD")
                .contains("orgTag=OPS");
        assertThat(permissionFilter.isBool()).isTrue();
        assertThat(permissionFilter.bool().minimumShouldMatch()).isEqualTo("1");
    }

    private void assertPublicOnlyFilter(Query permissionFilter) {
        assertThat(permissionFilter.isTerm()).isTrue();
        assertThat(permissionFilter.term().field()).isEqualTo("isPublic");
        assertThat(permissionFilter.term().value().booleanValue()).isTrue();
    }

    private List<String> flattenTerms(Query query) {
        List<String> terms = new ArrayList<>();
        collectTerms(query, terms);
        return terms;
    }

    private void collectTerms(Query query, List<String> terms) {
        if (query.isTerm()) {
            terms.add(query.term().field() + "=" + termValue(query));
            return;
        }
        if (!query.isBool()) {
            return;
        }
        query.bool().should().forEach(child -> collectTerms(child, terms));
        query.bool().filter().forEach(child -> collectTerms(child, terms));
        query.bool().must().forEach(child -> collectTerms(child, terms));
    }

    private String termValue(Query query) {
        if (query.term().value().isString()) {
            return query.term().value().stringValue();
        }
        if (query.term().value().isBoolean()) {
            return Boolean.toString(query.term().value().booleanValue());
        }
        if (query.term().value().isLong()) {
            return Long.toString(query.term().value().longValue());
        }
        if (query.term().value().isDouble()) {
            return Double.toString(query.term().value().doubleValue());
        }
        return query.term().value()._toJsonString();
    }
}
