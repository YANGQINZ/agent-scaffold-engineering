package com.ai.agent.domain.knowledge.service.rag;

import com.ai.agent.domain.agent.model.entity.WorkflowNode;
import com.ai.agent.domain.knowledge.model.valobj.RagResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 节点级RAG增强服务 — 为WorkflowNode提供按节点粒度的RAG增强入口
 * 检查节点的ragEnabled和knowledgeBaseId配置，满足条件时调用RagService管线
 * RAG失败时优雅降级，返回原始prompt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeRagService {

    private static final int DEFAULT_TOP_K = 5;

    private final RagService ragService;

    /**
     * 为节点应用 RAG 增强
     *
     * @param prompt 原始提示词
     * @param node   工作流节点（需配置ragEnabled和knowledgeBaseId）
     * @return 增强后的提示词，或RAG失败时的原始提示词
     */
    public String enhancePrompt(String prompt, WorkflowNode node) {
        if (!Boolean.TRUE.equals(node.getRagEnabled())) {
            return prompt;
        }
        if (node.getKnowledgeBaseId() == null || node.getKnowledgeBaseId().isBlank()) {
            log.warn("节点 {} 启用了RAG但未配置知识库ID，跳过RAG增强", node.getId());
            return prompt;
        }
        try {
            RagResult result = ragService.retrieveAndBuild(
                    node.getKnowledgeBaseId(), prompt, DEFAULT_TOP_K);
            return result.getEnhancedPrompt();
        } catch (Exception e) {
            log.error("节点 {} RAG增强失败，降级为原始prompt", node.getId(), e);
            return prompt;
        }
    }
}
