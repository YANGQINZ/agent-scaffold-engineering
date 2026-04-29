package com.ai.agent.infrastructure.rerank;

import com.ai.agent.domain.knowledge.model.entity.RerankDocument;
import com.ai.agent.domain.knowledge.model.entity.RerankItem;
import com.ai.agent.domain.knowledge.model.entity.RerankResult;
import com.ai.agent.domain.knowledge.model.entity.RerankUsage;
import com.ai.agent.domain.knowledge.model.valobj.RerankConfig;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云百炼Rerank API客户端 — 调用/v1/reranks端点进行语义精排
 */
@Slf4j
public class RerankApiClient {

    private static final String RERANK_API_URL = "https://dashscope.aliyuncs.com/compatible-api/v1/reranks";

    private final RestTemplate restTemplate;
    private final RerankConfig rerankConfig;
    private final String apiKey;

    public RerankApiClient(RestTemplate restTemplate, RerankConfig rerankConfig, String apiKey) {
        this.restTemplate = restTemplate;
        this.rerankConfig = rerankConfig;
        this.apiKey = apiKey;
    }

    /**
     * 调用阿里云百炼Rerank API
     *
     * @param query     查询文本
     * @param documents 候选文档内容列表
     * @param topN      返回Top-N
     * @return Rerank排序结果
     */
    public RerankResult callRerankApi(String query, List<String> documents, int topN) {
        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", rerankConfig.getModel());
        requestBody.put("query", query);
        requestBody.put("documents", new JSONArray(documents));
        requestBody.put("top_n", topN);
        requestBody.put("return_documents", true);

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);

        log.info("调用Rerank API: model={}, query={}, documents数量={}, topN={}",
                rerankConfig.getModel(), query, documents.size(), topN);

        ResponseEntity<String> response = restTemplate.exchange(
                RERANK_API_URL, HttpMethod.POST, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Rerank API调用失败: HTTP " + response.getStatusCode());
        }

        return parseResponse(response.getBody());
    }

    /**
     * 解析Rerank API响应
     */
    private RerankResult parseResponse(String responseBody) {
        JSONObject json = JSON.parseObject(responseBody);
        JSONObject output = json.getJSONObject("output");
        JSONObject usageJson = json.getJSONObject("usage");
        String requestId = json.getString("request_id");

        List<RerankItem> results = new ArrayList<>();
        if (output != null && output.containsKey("results")) {
            JSONArray resultsArray = output.getJSONArray("results");
            for (int i = 0; i < resultsArray.size(); i++) {
                JSONObject item = resultsArray.getJSONObject(i);
                RerankDocument document = null;
                if (item.containsKey("document")) {
                    JSONObject docJson = item.getJSONObject("document");
                    document = RerankDocument.builder()
                            .text(docJson.getString("text"))
                            .build();
                }
                results.add(RerankItem.builder()
                        .document(document)
                        .index(item.getInteger("index"))
                        .relevanceScore(item.getDouble("relevance_score"))
                        .build());
            }
        }

        RerankUsage usage = null;
        if (usageJson != null) {
            usage = RerankUsage.builder()
                    .totalTokens(usageJson.getInteger("total_tokens"))
                    .build();
        }

        return RerankResult.builder()
                .results(results)
                .usage(usage)
                .requestId(requestId)
                .build();
    }

}
