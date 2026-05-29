package com.yizhaoqi.smartpai.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
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
import java.util.stream.Collectors;

/**
 * 混合搜索服务，结合文本匹配和向量相似度搜索
 * 支持权限过滤，确保用户只能搜索其有权限访问的文档
 */
@Service
public class HybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchService.class);

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

            // 生成查询向量
            final List<Float> queryVector = embedToVectorList(query);
            final String recallQuery = expandQueryForRecall(query);
            logger.debug("检索召回查询: {}", recallQuery);

            // 如果向量生成失败，仅使用文本匹配
            if (queryVector == null) {
                logger.warn("向量生成失败，仅使用文本匹配进行搜索");
                return textOnlySearchWithPermission(query, userDbId, userEffectiveTags, topK);
            }

            logger.debug("向量生成成功，开始执行混合搜索 KNN");

            SearchResponse<EsDocument> response = esClient.search(s -> {
                        s.index("knowledge_base");
                        // KNN 召回
                        int recallK = topK * 30; // KNN 召回窗口
                        s.knn(kn -> kn
                                .field("vector")
                                .queryVector(queryVector)
                                .k(recallK)
                                .numCandidates(recallK)
                        );
                        // 必须命中关键词 + 权限过滤
                        s.query(q -> q.bool(b -> b
                                .must(mst -> mst.match(m -> m.field("textContent").query(recallQuery)))
                                .filter(f -> f.bool(bf -> bf
                                        // 条件1: 用户可访问自己的文档
                                        .should(s1 -> s1.term(t -> t.field("userId").value(userDbId)))
                                        // 条件2: 公开文档
                                        .should(s2 -> s2.term(t -> t.field("public").value(true)))
                                        // 条件3: 组织标签
                                        .should(s3 -> {
                                            if (userEffectiveTags.isEmpty()) {
                                                return s3.matchNone(mn -> mn);
                                            } else if (userEffectiveTags.size() == 1) {
                                                return s3.term(t -> t.field("orgTag").value(userEffectiveTags.get(0)));
                                            } else {
                                                return s3.bool(inner -> {
                                                    userEffectiveTags.forEach(tag -> inner.should(sh2 -> sh2.term(t -> t.field("orgTag").value(tag))));
                                                    return inner;
                                                });
                                            }
                                        })
                                ))
                        ));

                        // 第二阶段 BM25 rescore
                        s.rescore(r -> r
                                .windowSize(recallK)
                                .query(rq -> rq
                                        .queryWeight(0.2d)               // 保留部分 KNN 分
                                        .rescoreQueryWeight(1.0d)        // BM25 主导
                                        .query(rqq -> rqq.match(m -> m
                                                .field("textContent")
                                                .query(recallQuery)
                                        ))
                                )
                        );
                        s.size(topK);
                        return s;
                    }, EsDocument.class);

            logger.debug("Elasticsearch查询执行完成，命中数量: {}, 最大分数: {}", 
                response.hits().total().value(), response.hits().maxScore());

            List<SearchResult> results = response.hits().hits().stream()
                    .map(hit -> {
                        assert hit.source() != null;
                        logger.debug("搜索结果 - 文件: {}, 块: {}, 分数: {}, 内容: {}", 
                            hit.source().getFileMd5(), hit.source().getChunkId(), hit.score(), 
                            hit.source().getTextContent().substring(0, Math.min(50, hit.source().getTextContent().length())));
                        return new SearchResult(
                                hit.source().getFileMd5(),
                                hit.source().getChunkId(),
                                hit.source().getTextContent(),
                                hit.score(),
                                hit.source().getUserId(),
                                hit.source().getOrgTag(),
                                hit.source().isPublic()
                        );
                    })
                    .toList();

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

            SearchResponse<EsDocument> response = esClient.search(s -> s
                    .index("knowledge_base")
                    .query(q -> q
                            .bool(b -> b
                                    // 匹配内容相关性
                                    .must(m -> m
                                            .match(ma -> ma
                                                    .field("textContent")
                                                    .query(recallQuery)
                                            )
                                    )
                                    // 权限过滤
                                    .filter(f -> f
                                            .bool(bf -> bf
                                                    // 条件1: 用户可以访问自己的文档
                                                    .should(s1 -> s1
                                                            .term(t -> t
                                                                    .field("userId")
                                                                    .value(userDbId)
                                                            )
                                                    )
                                                    // 条件2: 用户可以访问公开的文档
                                                    .should(s2 -> s2
                                                            .term(t -> t
                                                                    .field("public")
                                                                    .value(true)
                                                            )
                                                    )
                                                    // 条件3: 用户可以访问其所属组织的文档（包含层级关系）
                                                    .should(s3 -> {
                                                        if (userEffectiveTags.isEmpty()) {
                                                            return s3.matchNone(mn -> mn);
                                                        } else if (userEffectiveTags.size() == 1) {
                                                            // 单个标签使用 term 查询
                                                            return s3.term(t -> t
                                                                    .field("orgTag")
                                                                    .value(userEffectiveTags.get(0))
                                                            );
                                                        } else {
                                                            // 多个标签使用 bool should 组合多个 term 查询
                                                            return s3.bool(innerBool -> {
                                                                userEffectiveTags.forEach(tag ->
                                                                        innerBool.should(sh -> sh.term(t -> t
                                                                                .field("orgTag")
                                                                                .value(tag)
                                                                        ))
                                                                );
                                                                return innerBool;
                                                            });
                                                        }
                                                    })
                                            )
                                    )
                            )
                    )
                    .minScore(0.3d)
                    .size(topK),
                    EsDocument.class
            );

            logger.debug("纯文本查询执行完成，命中数量: {}, 最大分数: {}", 
                response.hits().total().value(), response.hits().maxScore());

            List<SearchResult> results = response.hits().hits().stream()
                    .map(hit -> {
                        assert hit.source() != null;
                        logger.debug("纯文本搜索结果 - 文件: {}, 块: {}, 分数: {}, 内容: {}", 
                            hit.source().getFileMd5(), hit.source().getChunkId(), hit.score(), 
                            hit.source().getTextContent().substring(0, Math.min(50, hit.source().getTextContent().length())));
                        return new SearchResult(
                                hit.source().getFileMd5(),
                                hit.source().getChunkId(),
                                hit.source().getTextContent(),
                                hit.score(),
                                hit.source().getUserId(),
                                hit.source().getOrgTag(),
                                hit.source().isPublic()
                        );
                    })
                    .toList();

            logger.debug("返回纯文本搜索结果数量: {}", results.size());
            attachFileNames(results);
            return results;
        } catch (Exception e) {
            logger.error("纯文本搜索失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 原始搜索方法，不包含权限过滤，保留向后兼容性
     */
    public List<SearchResult> search(String query, int topK) {
        try {
            logger.debug("开始混合检索，查询: {}, topK: {}", query, topK);
            logger.warn("使用了没有权限过滤的搜索方法，建议使用 searchWithPermission 方法");

            // 生成查询向量
            final List<Float> queryVector = embedToVectorList(query);
            final String recallQuery = expandQueryForRecall(query);
            logger.debug("检索召回查询: {}", recallQuery);
            
            // 如果向量生成失败，仅使用文本匹配
            if (queryVector == null) {
                logger.warn("向量生成失败，仅使用文本匹配进行搜索");
                return textOnlySearch(query, topK);
            }

            SearchResponse<EsDocument> response = esClient.search(s -> {
                        s.index("knowledge_base");
                        int recallK = topK * 30;
                        s.knn(kn -> kn
                                .field("vector")
                                .queryVector(queryVector)
                                .k(recallK)
                                .numCandidates(recallK)
                        );

                        // 过滤仅保留包含关键词的文本
                        s.query(q -> q.match(m -> m.field("textContent").query(recallQuery)));

                        // rescore BM25
                        s.rescore(r -> r
                                .windowSize(recallK)
                                .query(rq -> rq
                                        .queryWeight(0.2d)
                                        .rescoreQueryWeight(1.0d)
                                        .query(rqq -> rqq.match(m -> m
                                                .field("textContent")
                                                .query(recallQuery)
                                        ))
                                )
                        );
                        s.size(topK);
                        return s;
                    }, EsDocument.class);

            return response.hits().hits().stream()
                    .map(hit -> {
                        assert hit.source() != null;
                        return new SearchResult(
                                hit.source().getFileMd5(),
                                hit.source().getChunkId(),
                                hit.source().getTextContent(),
                                hit.score()
                        );
                    })
                    .toList();
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
        SearchResponse<EsDocument> response = esClient.search(s -> s
                .index("knowledge_base")
                .query(q -> q
                        .match(m -> m
                                .field("textContent")
                                .query(recallQuery)
                        )
                )
                .size(topK),
                EsDocument.class
        );

        return response.hits().hits().stream()
                .map(hit -> {
                    assert hit.source() != null;
                    return new SearchResult(
                            hit.source().getFileMd5(),
                            hit.source().getChunkId(),
                            hit.source().getTextContent(),
                            hit.score()
                    );
                })
                .toList();
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
                new String[]{"调度原则", "运行原则", "控制原则", "总控制原则", "分期控制原则", "防洪为主", "兼顾发电"}
        );

        addTermsIfTriggered(
                terms,
                normalized,
                new String[]{"水位", "汛限", "限制水位", "蓄水位", "死水位", "库水位", "梅汛", "台汛", "非汛", "汛期", "兴利下限"},
                new String[]{"汛限水位", "限制水位", "梅汛期", "台汛期", "非汛期", "正常蓄水位", "死水位"}
        );

        addTermsIfTriggered(
                terms,
                normalized,
                new String[]{"开闸", "放水", "泄洪", "泄流", "下泄", "预泄", "放空", "闸门", "满发", "操作", "怎么处理", "如何处理", "大降雨", "台风", "暴雨"},
                new String[]{"开闸", "放水", "泄洪", "预泄", "放空管", "电站满发"}
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
