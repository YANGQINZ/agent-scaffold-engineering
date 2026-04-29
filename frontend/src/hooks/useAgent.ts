/**
 * Agent CRUD + 画布同步 Hook
 * 封装 Agent API 操作，自动同步到 Canvas Store
 */
import { useCallback } from 'react';
import {
  listAgents,
  getAgent,
  createAgent,
  updateAgent,
  deleteAgent,
  type AgentDefinition,
} from '@/api/agent';
import { useCanvasStore } from '@/stores/canvas';
import { useChatStore } from '@/stores/chat';
import type { Node, Edge } from '@xyflow/react';

/** 从 Agent 定义的 graphNodes / graphEdges 转换为 React Flow 节点 */
function agentToNodes(agent: AgentDefinition): Node[] {
  if (!agent.graphNodes?.length) return [];
  return agent.graphNodes.map((n) => ({
    id: n.id,
    type: 'agent', // 使用自定义 agent 节点类型
    position: { x: 0, y: 0 }, // 位置将由布局引擎计算
    data: {
      label: n.id,
      agentId: n.agentId,
      ragEnabled: n.ragEnabled,
      knowledgeBaseId: n.knowledgeBaseId,
    },
  }));
}

/** 从 Agent 定义的 graphEdges 转换为 React Flow 边 */
function agentToEdges(agent: AgentDefinition): Edge[] {
  if (!agent.graphEdges?.length) return [];
  return agent.graphEdges.map((e) => ({
    id: `${e.from}-${e.to}`,
    source: e.from,
    target: e.to,
    label: e.condition,
  }));
}

export function useAgent() {
  const setAgents = useCanvasStore((s) => s.setAgents);
  const setNodes = useCanvasStore((s) => s.setNodes);
  const setEdges = useCanvasStore((s) => s.setEdges);
  const setSelectedAgentId = useChatStore((s) => s.setSelectedAgentId);

  /**
   * 加载 Agent 列表，同步到 Canvas Store
   */
  const loadAgents = useCallback(async () => {
    const agents = await listAgents();
    setAgents(agents);
    return agents;
  }, [setAgents]);

  /**
   * 选中 Agent：设置选中 ID，将节点/边加载到画布
   */
  const selectAgent = useCallback(
    async (agentId: string) => {
      setSelectedAgentId(agentId);
      const agent = await getAgent(agentId);
      setNodes(agentToNodes(agent));
      setEdges(agentToEdges(agent));
      return agent;
    },
    [setSelectedAgentId, setNodes, setEdges],
  );

  /**
   * 保存 Agent（创建或更新）
   * 如果 data 中包含 agentId 且已存在则更新，否则创建
   */
  const saveAgent = useCallback(
    async (data: AgentDefinition) => {
      let saved: AgentDefinition;
      if (data.agentId) {
        saved = await updateAgent(data.agentId, data);
      } else {
        saved = await createAgent(data);
      }
      // 刷新列表
      await loadAgents();
      return saved;
    },
    [loadAgents],
  );

  /**
   * 删除 Agent 并刷新列表
   */
  const removeAgent = useCallback(
    async (agentId: string) => {
      await deleteAgent(agentId);
      await loadAgents();
    },
    [loadAgents],
  );

  return {
    loadAgents,
    selectAgent,
    saveAgent,
    removeAgent,
  };
}
