package com.yizhaoqi.smartpai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.yizhaoqi.smartpai.client.EmbeddingClient;
import com.yizhaoqi.smartpai.entity.EsDocument;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.model.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * 混合搜索服务，结合文本匹配和向量相似度搜索
 * 支持权限过滤，确保用户只能搜索其有权限访问的文档
 */
@Service
public class HybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchService.class);
    private static final int BM25_RECALL_MULTIPLIER = 20;
    private static final int VECTOR_RECALL_MULTIPLIER = 30;
    private static final int VECTOR_NUM_CANDIDATE_MULTIPLIER = 50;
    private static final int RRF_RANK_CONSTANT = 60;
    private static final double RRF_BM25_WEIGHT = 0.8d;
    private static final double RRF_VECTOR_WEIGHT = 1.0d;

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgTagCacheService orgTagCacheService;

    @Autowired
    private FileUploadRepository fileUploadRepository;

    private SearchReranker searchReranker = SearchReranker.identity();

    @Autowired(required = false)
    void setSearchReranker(SearchReranker searchReranker) {
        if (searchReranker != null) {
            this.searchReranker = searchReranker;
        }
    }

    /**
     * 使用文本匹配和向量相似度进行混合搜索，支持权限过滤
     * 该方法确保用户只能搜索其有权限访问的文档（自己的文档、公开文档、所属组织的文档）
     *
     * @param query  查询字符串
     * @param userId 用户ID
     * @param topK   返回结果数量
     * @return 搜索结果列表
     */
    public List<SearchResult> searchWithPermission(String query, String userId, int topK) {
        logger.debug("开始带权限搜索，查询: {}, 用户ID: {}", query, userId);
        
        try {
            // 获取用户有效的组织标签（包含层级关系）
            List<String> userEffectiveTags = getUserEffectiveOrgTags(userId);
            logger.debug("用户 {} 的有效组织标签: {}", userId, userEffectiveTags);

            // 获取用户的数据库ID用于权限过滤
            String userDbId = getUserDbId(userId);
            logger.debug("用户 {} 的数据库ID: {}", userId, userDbId);

            final String recallQuery = expandQueryForRecall(query);
            logger.debug("检索召回查询: {}", recallQuery);
            int resultK = normalizeTopK(topK);
            int bm25RecallK = bm25RecallK(resultK);
            int vectorRecallK = vectorRecallK(resultK);

            final List<Float> queryVector = embedToVectorList(query);
            if (queryVector == null) {
                logger.warn("向量生成失败，回退到纯 BM25 搜索");
                return textOnlySearchWithPermission(query, userDbId, userEffectiveTags, resultK);
            }

            CompletableFuture<List<SearchResult>> bm25Future = recallAsync(
                    () -> searchBm25WithPermission(recallQuery, userDbId, userEffectiveTags, bm25RecallK)
            );
            CompletableFuture<List<SearchResult>> vectorFuture = recallAsync(
                    () -> searchVectorWithPermission(queryVector, userDbId, userEffectiveTags, vectorRecallK)
            );
            List<SearchResult> bm25Results = bm25Future.join();
            List<SearchResult> vectorResults = vectorFuture.join();
            logger.debug("双路并行召回完成，BM25 候选数量: {}, 向量候选数量: {}", bm25Results.size(), vectorResults.size());

            List<SearchResult> results = applyReranker(query, mergeByRrf(bm25Results, vectorResults, resultK), resultK);
            logger.debug("返回搜索结果数量: {}", results.size());
            attachFileNames(results);
            return results;
        } catch (Exception e) {
            logger.error("带权限的搜索失败", e);
            // 发生异常时尝试使用纯文本搜索作为后备方案
            try {
                logger.info("尝试使用纯文本搜索作为后备方案");
                return textOnlySearchWithPermission(query, getUserDbId(userId), getUserEffectiveOrgTags(userId), topK);
            } catch (Exception fallbackError) {
                logger.error("后备搜索也失败", fallbackError);
                return Collections.emptyList();
            }
        }
    }

    /**
     * 仅使用文本匹配的带权限搜索方法
     */
    private List<SearchResult> textOnlySearchWithPermission(String query, String userDbId, List<String> userEffectiveTags, int topK) {
        try {
            logger.debug("开始执行纯文本搜索，用户数据库ID: {}, 标签: {}", userDbId, userEffectiveTags);
            final String recallQuery = expandQueryForRecall(query);
            logger.debug("纯文本召回查询: {}", recallQuery);
            int resultK = normalizeTopK(topK);
            List<SearchResult> results = limitResults(
                    searchBm25WithPermission(recallQuery, userDbId, userEffectiveTags, bm25RecallK(resultK)),
                    resultK
            );
            logger.debug("返回纯文本搜索结果数量: {}", results.size());
            attachFileNames(results);
            return results;
        } catch (Exception e) {
            logger.error("纯文本搜索失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 匿名/兼容搜索方法，仅返回公开文档。
     */
    public List<SearchResult> search(String query, int topK) {
        try {
            logger.debug("开始混合检索，查询: {}, topK: {}", query, topK);
            logger.debug("未提供用户身份，搜索范围限制为公开文档");

            final String recallQuery = expandQueryForRecall(query);
            logger.debug("检索召回查询: {}", recallQuery);
            int resultK = normalizeTopK(topK);
            int bm25RecallK = bm25RecallK(resultK);
            int vectorRecallK = vectorRecallK(resultK);
            
            final List<Float> queryVector = embedToVectorList(query);
            if (queryVector == null) {
                logger.warn("向量生成失败，回退到纯 BM25 搜索");
                return textOnlySearch(query, resultK);
            }

            CompletableFuture<List<SearchResult>> bm25Future = recallAsync(
                    () -> searchBm25WithPermission(recallQuery, null, Collections.emptyList(), bm25RecallK)
            );
            CompletableFuture<List<SearchResult>> vectorFuture = recallAsync(
                    () -> searchVectorWithPermission(queryVector, null, Collections.emptyList(), vectorRecallK)
            );
            List<SearchResult> bm25Results = bm25Future.join();
            List<SearchResult> vectorResults = vectorFuture.join();
            logger.debug("双路并行召回完成，BM25 候选数量: {}, 向量候选数量: {}", bm25Results.size(), vectorResults.size());

            List<SearchResult> results = applyReranker(query, mergeByRrf(bm25Results, vectorResults, resultK), resultK);
            attachFileNames(results);
            return results;
        } catch (Exception e) {
            logger.error("搜索失败", e);
            // 发生异常时尝试使用纯文本搜索作为后备方案
            try {
                logger.info("尝试使用纯文本搜索作为后备方案");
                return textOnlySearch(query, topK);
            } catch (Exception fallbackError) {
                logger.error("后备搜索也失败", fallbackError);
                throw new RuntimeException("搜索完全失败", fallbackError);
            }
        }
    }

    /**
     * 仅使用文本匹配的搜索方法
     */
    private List<SearchResult> textOnlySearch(String query, int topK) throws Exception {
        final String recallQuery = expandQueryForRecall(query);
        int resultK = normalizeTopK(topK);
        List<SearchResult> results = limitResults(
                searchBm25WithPermission(recallQuery, null, Collections.emptyList(), bm25RecallK(resultK)),
                resultK
        );
        attachFileNames(results);
        return results;
    }

    private List<SearchResult> searchBm25WithPermission(String recallQuery,
                                                        String userDbId,
                                                        List<String> userEffectiveTags,
                                                        int recallK) throws Exception {
        SearchResponse<EsDocument> response = esClient.search(
                buildBm25SearchRequest(recallQuery, userDbId, userEffectiveTags, recallK),
                EsDocument.class
        );

        return toSearchResults(response);
    }

    private List<SearchResult> searchVectorWithPermission(List<Float> queryVector,
                                                          String userDbId,
                                                          List<String> userEffectiveTags,
                                                          int recallK) throws Exception {
        SearchResponse<EsDocument> response = esClient.search(
                buildVectorSearchRequest(queryVector, userDbId, userEffectiveTags, recallK),
                EsDocument.class
        );

        return toSearchResults(response);
    }

    SearchRequest buildBm25SearchRequest(String recallQuery,
                                         String userDbId,
                                         List<String> userEffectiveTags,
                                         int recallK) {
        Query permissionQuery = buildPermissionQuery(userDbId, userEffectiveTags);
        return SearchRequest.of(s -> s
                .index("knowledge_base")
                .query(q -> q
                        .bool(b -> b
                                .must(m -> m.match(ma -> ma
                                        .field("textContent")
                                        .query(recallQuery)
                                ))
                                .filter(permissionQuery)
                        )
                )
                .size(recallK)
        );
    }

    SearchRequest buildVectorSearchRequest(List<Float> queryVector,
                                           String userDbId,
                                           List<String> userEffectiveTags,
                                           int recallK) {
        Query permissionQuery = buildPermissionQuery(userDbId, userEffectiveTags);
        return SearchRequest.of(s -> s
                .index("knowledge_base")
                .knn(kn -> kn
                        .field("vector")
                        .queryVector(queryVector)
                        .k(recallK)
                        .numCandidates(vectorNumCandidatesK(recallK))
                        .filter(permissionQuery)
                )
                .size(recallK)
        );
    }

    Query buildPermissionQuery(String userDbId, List<String> userEffectiveTags) {
        boolean hasUser = userDbId != null && !userDbId.isBlank();
        boolean hasTags = userEffectiveTags != null && !userEffectiveTags.isEmpty();
        if (!hasUser && !hasTags) {
            return Query.of(q -> q.term(t -> t.field("isPublic").value(true)));
        }

        return Query.of(q -> q.bool(b -> {
            if (hasUser) {
                b.should(s -> s.term(t -> t.field("userId").value(userDbId)));
            }
            b.should(s -> s.term(t -> t.field("isPublic").value(true)));

            if (hasTags) {
                if (userEffectiveTags.size() == 1) {
                    b.should(s -> s.term(t -> t.field("orgTag").value(userEffectiveTags.get(0))));
                } else {
                    b.should(s -> s.bool(inner -> {
                        userEffectiveTags.forEach(tag ->
                                inner.should(sh -> sh.term(t -> t.field("orgTag").value(tag)))
                        );
                        inner.minimumShouldMatch("1");
                        return inner;
                    }));
                }
            }

            b.minimumShouldMatch("1");
            return b;
        }));
    }

    private CompletableFuture<List<SearchResult>> recallAsync(SearchSupplier supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    List<SearchResult> applyReranker(String query, List<SearchResult> mergedResults, int topK) {
        List<SearchResult> reranked = searchReranker.rerank(query, mergedResults, normalizeTopK(topK));
        if (reranked == null) {
            return mergedResults;
        }
        return limitResults(reranked, topK);
    }

    @FunctionalInterface
    private interface SearchSupplier {
        List<SearchResult> get() throws Exception;
    }

    List<SearchResult> mergeByRrf(List<SearchResult> bm25Results, List<SearchResult> vectorResults, int topK) {
        Map<String, RrfCandidate> candidates = new LinkedHashMap<>();
        addRrfScores(candidates, bm25Results, RRF_BM25_WEIGHT);
        addRrfScores(candidates, vectorResults, RRF_VECTOR_WEIGHT);

        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(RrfCandidate::score).reversed())
                .limit(normalizeTopK(topK))
                .map(candidate -> copyWithScore(candidate.result(), candidate.score()))
                .toList();
    }

    private void addRrfScores(Map<String, RrfCandidate> candidates, List<SearchResult> results, double weight) {
        if (results == null || results.isEmpty()) {
            return;
        }

        Set<String> seenInRoute = new HashSet<>();
        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            String key = resultKey(result);
            if (key == null || !seenInRoute.add(key)) {
                continue;
            }

            int rank = i + 1;
            double score = weight / (RRF_RANK_CONSTANT + rank);
            candidates.compute(key, (ignored, candidate) -> {
                if (candidate == null) {
                    return new RrfCandidate(result, score);
                }
                candidate.addScore(score);
                return candidate;
            });
        }
    }

    private String resultKey(SearchResult result) {
        if (result == null || result.getFileMd5() == null || result.getChunkId() == null) {
            return null;
        }
        return result.getFileMd5() + ":" + result.getChunkId();
    }

    private SearchResult copyWithScore(SearchResult source, double score) {
        return new SearchResult(
                source.getFileMd5(),
                source.getChunkId(),
                source.getTextContent(),
                score,
                source.getUserId(),
                source.getOrgTag(),
                Boolean.TRUE.equals(source.getIsPublic()),
                source.getFileName()
        );
    }

    private List<SearchResult> toSearchResults(SearchResponse<EsDocument> response) {
        return response.hits().hits().stream()
                .map(this::toSearchResult)
                .toList();
    }

    private SearchResult toSearchResult(Hit<EsDocument> hit) {
        assert hit.source() != null;
        EsDocument source = hit.source();
        logger.debug("搜索候选 - 文件: {}, 块: {}, 分数: {}, 内容: {}",
                source.getFileMd5(),
                source.getChunkId(),
                hit.score(),
                source.getTextContent() == null ? "" : source.getTextContent().substring(0, Math.min(50, source.getTextContent().length())));
        return new SearchResult(
                source.getFileMd5(),
                source.getChunkId(),
                source.getTextContent(),
                hit.score(),
                source.getUserId(),
                source.getOrgTag(),
                source.isPublic()
        );
    }

    private List<SearchResult> limitResults(List<SearchResult> results, int topK) {
        return results.stream()
                .limit(normalizeTopK(topK))
                .toList();
    }

    private int normalizeTopK(int topK) {
        return Math.max(1, topK);
    }

    private int bm25RecallK(int topK) {
        return normalizeTopK(topK) * BM25_RECALL_MULTIPLIER;
    }

    private int vectorRecallK(int topK) {
        return normalizeTopK(topK) * VECTOR_RECALL_MULTIPLIER;
    }

    private int vectorNumCandidatesK(int vectorRecallK) {
        return Math.max(
                normalizeTopK(vectorRecallK),
                (int) Math.ceil((double) normalizeTopK(vectorRecallK) * VECTOR_NUM_CANDIDATE_MULTIPLIER / VECTOR_RECALL_MULTIPLIER)
        );
    }

    private static class RrfCandidate {
        private final SearchResult result;
        private double score;

        private RrfCandidate(SearchResult result, double score) {
            this.result = result;
            this.score = score;
        }

        private SearchResult result() {
            return result;
        }

        private double score() {
            return score;
        }

        private void addScore(double score) {
            this.score += score;
        }
    }

    String expandQueryForRecall(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }

        List<String> terms = new ArrayList<>();
        String normalized = query.trim();
        terms.add(normalized);

        addTermsIfTriggered(
                terms,
                normalized,
                new String[]{"原则", "规则", "要求", "调度原则", "运行原则", "控制原则", "总控制", "分期控制", "怎么运行", "如何运行", "怎么调度", "如何调度"},
                new String[]{"调度原则", "运行原则", "控制原则", "总控制原则", "分期控制原则", "防洪为主", "兼顾发电", "兴利", "灌溉"}
        );

        addTermsIfTriggered(
                terms,
                normalized,
                new String[]{"水位", "汛限", "限制水位", "蓄水位", "死水位", "库水位", "梅汛", "台汛", "非汛", "汛期", "兴利下限"},
                new String[]{"汛限水位", "限制水位", "控制水位", "库水位", "梅汛期", "台汛期", "非汛期", "正常蓄水位", "死水位", "307.00m", "310.00m", "314.00m", "298.00m"}
        );

        addTermsIfTriggered(
                terms,
                normalized,
                new String[]{"开闸", "放水", "泄洪", "泄流", "下泄", "预泄", "放空", "闸门", "满发", "大降雨", "台风", "暴雨"},
                new String[]{"开闸", "放水", "泄洪", "预泄", "调蓄", "降低水位", "溢洪道", "闸门"}
        );

        addTermsIfTriggered(
                terms,
                normalized,
                new String[]{"保护对象", "行政村", "村", "责任人", "巡查", "联系人", "联系方式", "电话", "手机"},
                new String[]{"防洪保护对象", "行政村", "巡查责任人", "联系方式"}
        );

        if (containsAny(normalized, "位置", "位于", "哪里", "在哪", "地点", "坐标", "坝址")) {
            terms.add("位于");
            terms.add("所在");
            terms.add("地处");
            terms.add("坝址");
            terms.add("支流");
            terms.add("上游");
        }

        if (containsAny(normalized, "流域", "水系")) {
            terms.add("流域");
            terms.add("支流");
            terms.add("主流");
            terms.add("发源");
            terms.add("注入");
            terms.add("面积");
        }

        if (containsAny(normalized, "概况", "介绍", "简介", "基本情况")) {
            terms.add("基本情况");
            terms.add("水库概况");
            terms.add("工程情况");
            terms.add("控制运行计划");
        }

        return terms.stream()
                .filter(term -> term != null && !term.isBlank())
                .distinct()
                .collect(Collectors.joining(" "));
    }

    private void addTermsIfTriggered(List<String> terms, String query, String[] triggers, String[] expandedTerms) {
        if (!containsAny(query, triggers)) {
            return;
        }
        Collections.addAll(terms, expandedTerms);
    }

    private boolean containsAny(String text, String... candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成查询向量，返回 List<Float>，失败时返回 null
     */
    private List<Float> embedToVectorList(String text) {
        try {
            List<float[]> vecs = embeddingClient.embed(List.of(text));
            if (vecs == null || vecs.isEmpty()) {
                logger.warn("生成的向量为空");
                return null;
            }
            float[] raw = vecs.get(0);
            List<Float> list = new ArrayList<>(raw.length);
            for (float v : raw) {
                list.add(v);
            }
            return list;
        } catch (Exception e) {
            logger.error("生成向量失败", e);
            return null;
        }
    }
    
    /**
     * 获取用户的有效组织标签（包含层级关系）
     */
    private List<String> getUserEffectiveOrgTags(String userId) {
        logger.debug("获取用户有效组织标签，用户ID: {}", userId);
        try {
            // 获取用户名
            User user;
            try {
                Long userIdLong = Long.parseLong(userId);
                logger.debug("解析用户ID为Long: {}", userIdLong);
                user = userRepository.findById(userIdLong)
                    .orElseThrow(() -> new CustomException("User not found with ID: " + userId, HttpStatus.NOT_FOUND));
                logger.debug("通过ID找到用户: {}", user.getUsername());
            } catch (NumberFormatException e) {
                // 如果userId不是数字格式，则假设它就是username
                logger.debug("用户ID不是数字格式，作为用户名查找: {}", userId);
                user = userRepository.findByUsername(userId)
                    .orElseThrow(() -> new CustomException("User not found: " + userId, HttpStatus.NOT_FOUND));
                logger.debug("通过用户名找到用户: {}", user.getUsername());
            }
            
            // 通过orgTagCacheService获取用户的有效标签集合
            List<String> effectiveTags = orgTagCacheService.getUserEffectiveOrgTags(user.getUsername());
            logger.debug("用户 {} 的有效组织标签: {}", user.getUsername(), effectiveTags);
            return effectiveTags;
        } catch (Exception e) {
            logger.error("获取用户有效组织标签失败: {}", e.getMessage(), e);
            return Collections.emptyList(); // 返回空列表作为默认值
        }
    }

    /**
     * 获取用户的数据库ID用于权限过滤
     */
    private String getUserDbId(String userId) {
        logger.debug("获取用户数据库ID，用户ID: {}", userId);
        try {
            // 获取用户名
            User user;
            try {
                Long userIdLong = Long.parseLong(userId);
                logger.debug("解析用户ID为Long: {}", userIdLong);
                user = userRepository.findById(userIdLong)
                    .orElseThrow(() -> new CustomException("User not found with ID: " + userId, HttpStatus.NOT_FOUND));
                logger.debug("通过ID找到用户: {}", user.getUsername());
                return userIdLong.toString(); // 如果输入已经是数字ID，直接返回
            } catch (NumberFormatException e) {
                // 如果userId不是数字格式，则假设它就是username
                logger.debug("用户ID不是数字格式，作为用户名查找: {}", userId);
                user = userRepository.findByUsername(userId)
                    .orElseThrow(() -> new CustomException("User not found: " + userId, HttpStatus.NOT_FOUND));
                logger.debug("通过用户名找到用户: {}, ID: {}", user.getUsername(), user.getId());
                return user.getId().toString(); // 返回用户的数据库ID
            }
        } catch (Exception e) {
            logger.error("获取用户数据库ID失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取用户数据库ID失败", e);
        }
    }

    private void attachFileNames(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        try {
            // 收集所有唯一的 fileMd5
            Set<String> md5Set = results.stream()
                    .map(SearchResult::getFileMd5)
                    .collect(Collectors.toSet());
            List<FileUpload> uploads = fileUploadRepository.findByFileMd5In(new java.util.ArrayList<>(md5Set));
            Map<String, String> md5ToName = uploads.stream()
                    .collect(Collectors.toMap(FileUpload::getFileMd5, FileUpload::getFileName));
            // 填充文件名
            results.forEach(r -> r.setFileName(md5ToName.get(r.getFileMd5())));
        } catch (Exception e) {
            logger.error("补充文件名失败", e);
        }
    }
}
