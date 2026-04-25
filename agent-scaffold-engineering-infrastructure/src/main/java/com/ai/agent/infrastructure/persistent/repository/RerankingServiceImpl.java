package com.ai.agent.infrastructure.persistent.repository;

import com.ai.agent.domain.knowledge.model.entity.RerankItem;
import com.ai.agent.domain.knowledge.model.entity.RerankResult;
import com.ai.agent.domain.knowledge.model.valobj.RerankConfig;
import com.ai.agent.domain.knowledge.service.RerankingService;
import com.ai.agent.infrastructure.rerank.RerankApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Reranking精排服务实现 — 调用阿里云百炼Rerank API，含重试机制
 */
@Slf4j
@Service
public class RerankingServiceImpl implements RerankingService {

    private final RerankApiClient rerankApiClient;
    private final RerankConfig rerankConfig;

    public RerankingServiceImpl(RerankConfig rerankConfig,
                                @Value("${AI_DASHSCOPE_API_KEY:}") String apiKey) {
        this.rerankConfig = rerankConfig;
        // 创建带超时配置的RestTemplate
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        RestTemplate restTemplate = new RestTemplate(factory);
        this.rerankApiClient = new RerankApiClient(restTemplate, rerankConfig, apiKey);
    }

    @Override
    public List<RerankItem> rerank(String query, List<String> documents, int topN) {
        if (!rerankConfig.getEnabled()) {
            log.info("Reranking未启用，跳过精排");
            return null;
        }

        if (documents == null || documents.isEmpty()) {
            log.warn("候选文档列表为空，跳过Rerank");
            return null;
        }

        int retryCount = rerankConfig.getRetryCount() != null ? rerankConfig.getRetryCount() : 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                RerankResult result = rerankApiClient.callRerankApi(query, documents, topN);
                if (result != null && result.getResults() != null) {
                    log.info("Rerank API调用成功: requestId={}, 结果数量={}",
                            result.getRequestId(), result.getResults().size());
                    return result.getResults();
                }
            } catch (RestClientException e) {
                lastException = e;
                String message = e.getMessage();
                // 认证失败（401/403）不重试，直接降级
                if (message != null && (message.contains("401") || message.contains("403") || message.contains("InvalidApiKey"))) {
                    log.error("Rerank API认证失败，不重试，直接降级: {}", message);
                    return null;
                }
                log.warn("Rerank API调用失败（第{}/{}次）: {}", attempt, retryCount, message);
                if (attempt < retryCount) {
                    try {
                        Thread.sleep(1000); // 重试间隔1秒
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Rerank API调用异常（第{}/{}次）: {}", attempt, retryCount, e.getMessage());
                if (attempt < retryCount) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }

        // 重试全部失败，降级使用RRF结果
        log.error("Rerank API调用全部失败（共{}次），降级使用RRF结果: {}",
                retryCount, lastException != null ? lastException.getMessage() : "未知错误");
        return null;
    }

}
