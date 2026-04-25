package com.ai.agent.domain.knowledge.service;

import com.ai.agent.domain.knowledge.model.entity.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * MMR多样性重排服务 — 基于最大边际相关性算法对检索结果进行多样性去重
 * 迭代选取最大化 lambda*sim(d,q) - (1-lambda)*max_sim(d,R') 的片段，
 * 在保持相关性的同时减少语义冗余
 */
@Slf4j
@Service
public class MmrService {

    /**
     * 基于最大边际相关性(MMR)的多样性重排
     *
     * @param queryEmbedding 查询向量
     * @param chunks         候选片段列表（需含embedding）
     * @param lambda         相关性vs多样性权衡系数（0=最大多样性，1=最大相关性）
     * @param topK           返回片段数
     * @return 多样性重排后的片段列表
     */
    public List<DocumentChunk> rerankByMmr(float[] queryEmbedding, List<DocumentChunk> chunks,
                                           double lambda, int topK) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        // 过滤掉没有embedding的片段
        List<DocumentChunk> validChunks = chunks.stream()
                .filter(c -> c.getEmbedding() != null && c.getEmbedding().length > 0)
                .toList();

        if (validChunks.isEmpty()) {
            log.warn("所有候选片段均无embedding，无法执行MMR，返回原始列表");
            return chunks.stream().limit(topK).toList();
        }

        // 预计算所有片段与查询的相似度
        Map<Integer, Double> querySimMap = new HashMap<>();
        for (int i = 0; i < validChunks.size(); i++) {
            querySimMap.put(i, cosineSimilarity(queryEmbedding, validChunks.get(i).getEmbedding()));
        }

        List<DocumentChunk> selected = new ArrayList<>();
        Set<Integer> selectedIndices = new HashSet<>();
        Set<Integer> remainingIndices = new HashSet<>();
        for (int i = 0; i < validChunks.size(); i++) {
            remainingIndices.add(i);
        }

        // 第一步：选择与查询最相似的片段
        int firstIdx = querySimMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);
        selected.add(validChunks.get(firstIdx));
        selectedIndices.add(firstIdx);
        remainingIndices.remove(firstIdx);

        // 迭代选取最大化MMR分数的片段
        while (selected.size() < topK && !remainingIndices.isEmpty()) {
            int bestIdx = -1;
            double bestMmrScore = Double.NEGATIVE_INFINITY;

            for (int idx : remainingIndices) {
                double querySim = querySimMap.get(idx);

                // 计算与已选片段集的最大相似度
                double maxSelectedSim = 0.0;
                for (int selIdx : selectedIndices) {
                    double sim = cosineSimilarity(
                            validChunks.get(idx).getEmbedding(),
                            validChunks.get(selIdx).getEmbedding());
                    maxSelectedSim = Math.max(maxSelectedSim, sim);
                }

                // MMR分数 = lambda * sim(d,q) - (1-lambda) * max_sim(d,R')
                double mmrScore = lambda * querySim - (1 - lambda) * maxSelectedSim;
                if (mmrScore > bestMmrScore) {
                    bestMmrScore = mmrScore;
                    bestIdx = idx;
                }
            }

            if (bestIdx >= 0) {
                selected.add(validChunks.get(bestIdx));
                selectedIndices.add(bestIdx);
                remainingIndices.remove(bestIdx);
            } else {
                break;
            }
        }

        log.info("MMR重排完成: 输入{}个片段, 输出{}个片段, lambda={}", validChunks.size(), selected.size(), lambda);
        return selected;
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0.0) {
            return 0.0;
        }
        return dotProduct / denominator;
    }

}
