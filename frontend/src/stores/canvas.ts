/**
 * 画布状态管理
 * 管理工作流画布中的节点、连线、Agent 定义及节点运行状态
 * 使用 @xyflow/react 的 Node / Edge 类型
 */
import { create } from 'zustand';
import {
  type Node,
  type Edge,
  type OnNodesChange,
  type OnEdgesChange,
  applyNodeChanges,
  applyEdgeChanges,
} from '@xyflow/react';
import type { AgentDefinition, EngineType } from '@/api/agent';

// ═══════════════════════════════════════════════════════════
// 类型定义
// ═══════════════════════════════════════════════════════════

/** 节点运行状态 */
export type NodeRunStatus = 'idle' | 'running' | 'done' | 'error';

/** 单个节点的运行状态 */
export interface NodeState {
  status: NodeRunStatus;
  result?: string;
}

// ═══════════════════════════════════════════════════════════
// 辅助函数
// ═══════════════════════════════════════════════════════════

/** 根据 agent 节点的 subEngine 自动推断引擎类型 */
function inferEngineType(agentNodes: Node[]): EngineType {
  const hasAgentscope = agentNodes.some((n) => (n.data as any).subEngine === 'AGENTSCOPE');
  const hasGraph = agentNodes.some((n) => (n.data as any).subEngine !== 'AGENTSCOPE');
  if (hasAgentscope && hasGraph) return 'HYBRID';
  if (hasAgentscope) return 'AGENTSCOPE';
  return 'GRAPH';
}

// ═══════════════════════════════════════════════════════════
// Store 定义
// ═══════════════════════════════════════════════════════════

interface CanvasState {
  /** 画布节点 */
  nodes: Node[];
  /** 画布连线 */
  edges: Edge[];
  /** 当前选中的节点 ID */
  selectedNodeId: string | null;
  /** Agent 定义列表 */
  agents: AgentDefinition[];
  /** 各节点运行状态映射 */
  nodeStates: Record<string, NodeState>;
  /** 当前编辑的 Agent ID */
  currentAgentId: string | null;
  /** 当前编辑的 Agent 名称 */
  currentAgentName: string;

  // ─── Actions ───

  /** 设置画布节点 */
  setNodes: (nodes: Node[]) => void;
  /** 设置画布连线 */
  setEdges: (edges: Edge[]) => void;
  /** 处理节点变更（React Flow 拖拽、选择等） */
  onNodesChange: OnNodesChange;
  /** 处理连线变更 */
  onEdgesChange: OnEdgesChange;
  /** 设置选中的节点 ID */
  setSelectedNodeId: (id: string | null) => void;
  /** 设置 Agent 定义列表 */
  setAgents: (agents: AgentDefinition[]) => void;
  /** 设置单个节点的运行状态 */
  setNodeState: (nodeId: string, state: NodeState) => void;
  /** 清空所有节点运行状态 */
  clearNodeStates: () => void;
  /** 设置当前编辑的 Agent 信息 */
  setCurrentAgent: (agentId: string | null, name: string) => void;
  /** 从 AgentDefinition 加载画布 */
  loadFromAgentDefinition: (agent: AgentDefinition) => void;
  /** 将画布导出为 AgentDefinition */
  exportToAgentDefinition: () => Omit<AgentDefinition, 'agentId'>;
}

export const useCanvasStore = create<CanvasState>((set, get) => ({
  nodes: [],
  edges: [],
  selectedNodeId: null,
  agents: [],
  nodeStates: {},
  currentAgentId: null,
  currentAgentName: '',

  setNodes: (nodes) => set({ nodes }),

  setEdges: (edges) => set({ edges }),

  onNodesChange: (changes) =>
    set((state) => ({
      nodes: applyNodeChanges(changes, state.nodes),
    })),

  onEdgesChange: (changes) =>
    set((state) => ({
      edges: applyEdgeChanges(changes, state.edges),
    })),

  setSelectedNodeId: (selectedNodeId) => set({ selectedNodeId }),

  setAgents: (agents) => set({ agents }),

  setNodeState: (nodeId, nodeState) =>
    set((state) => ({
      nodeStates: { ...state.nodeStates, [nodeId]: nodeState },
    })),

  clearNodeStates: () => set({ nodeStates: {} }),

  setCurrentAgent: (currentAgentId, currentAgentName) =>
    set({ currentAgentId, currentAgentName }),

  loadFromAgentDefinition: (agent) => {
    const nodes: Node[] = [];
    const edges: Edge[] = [];

    // 1. Start node
    nodes.push({
      id: 'start',
      type: 'start',
      position: { x: 400, y: 50 },
      data: { label: '开始' },
    });

    // 2. graphNodes → Agent nodes
    const agentNodeIds: string[] = [];
    (agent.graphNodes || []).forEach((gn, i) => {
      agentNodeIds.push(gn.id);
      nodes.push({
        id: gn.id,
        type: 'agent',
        position: {
          x: 150 + (i % 3) * 200,
          y: 150 + Math.floor(i / 3) * 150,
        },
        data: {
          label: gn.id,
          instruction: gn.instruction,
          subEngine: gn.subEngine || 'GRAPH',
          ragEnabled: gn.ragEnabled,
          knowledgeBaseId: gn.knowledgeBaseId,
          mcpServers: gn.mcpServers,
          agentId: gn.agentId,
        },
      });
    });

    // 3. graphStart → Start outgoing edges
    const starts = agent.graphStart || [];
    starts.forEach((target) => {
      edges.push({
        id: `e_start-${target}`,
        source: 'start',
        target,
        type: 'default',
      });
    });

    // 4. graphEdges → Agent-to-Agent edges
    const graphEdges = agent.graphEdges || [];
    graphEdges.forEach((e) => {
      edges.push({
        id: `e_${e.from}-${e.to}`,
        source: e.from,
        target: e.to,
        type: e.condition ? 'condition' : 'default',
        data: { condition: e.condition || undefined },
      });
    });

    // 5. Find leaf nodes (no outgoing edges)
    const nodesWithOutgoing = new Set(graphEdges.map((e) => e.from));
    const leafNodes = agentNodeIds.filter((id) => !nodesWithOutgoing.has(id));

    // 6. End node
    nodes.push({
      id: 'end',
      type: 'end',
      position: {
        x: 400,
        y: 150 + (Math.ceil(agentNodeIds.length / 3) + 1) * 150,
      },
      data: { label: '结束' },
    });

    // 7. Leaf → End edges
    leafNodes.forEach((leafId) => {
      edges.push({
        id: `e_${leafId}-end`,
        source: leafId,
        target: 'end',
        type: 'default',
      });
    });

    set({
      nodes,
      edges,
      currentAgentId: agent.agentId,
      currentAgentName: agent.name,
    });
  },

  exportToAgentDefinition: () => {
    const { nodes, edges, currentAgentId, currentAgentName } = get();
    const agentNodes = nodes.filter((n) => n.type === 'agent');
    const agentNodeIds = new Set(agentNodes.map((n) => n.id));

    // graphStart: all targets from start node's outgoing edges
    // 通过节点 type 查找 Start 节点（兼容手动添加的动态 ID）
    const startNodeIds = new Set(
      nodes.filter((n) => n.type === 'start').map((n) => n.id),
    );
    let graphStart = edges
      .filter((e) => startNodeIds.has(e.source) && agentNodeIds.has(e.target))
      .map((e) => e.target);

    if (graphStart.length === 0) {
      const nodesWithIncoming = new Set(
        edges
          .filter((e) => agentNodeIds.has(e.target))
          .map((e) => e.target),
      );
      graphStart = agentNodeIds
        ? agentNodes
            .filter((n) => !nodesWithIncoming.has(n.id))
            .map((n) => n.id)
        : [];
    }

    // graphNodes: all agent nodes with their data
    const graphNodes = agentNodes.map((n) => {
      const d = n.data as any;
      return {
        id: n.id,
        instruction: d.instruction || undefined,
        subEngine: d.subEngine || undefined,
        agentId: d.agentId || undefined,
        ragEnabled: d.ragEnabled || false,
        knowledgeBaseId: d.knowledgeBaseId || undefined,
        mcpServers: d.mcpServers || undefined,
      };
    });

    // graphEdges: agent-to-agent edges only
    const graphEdges = edges
      .filter((e) => agentNodeIds.has(e.source) && agentNodeIds.has(e.target))
      .map((e) => ({
        from: e.source,
        to: e.target,
        condition: (e.data as any)?.condition || undefined,
      }));

    // Auto-infer engine type
    const engine = inferEngineType(agentNodes);

    // AGENTSCOPE: 输出 agentscopeAgents 而非 graphNodes/graphEdges
    if (engine === 'AGENTSCOPE') {
      const agentscopeAgents = agentNodes.map((n) => {
        const d = n.data as any;
        return {
          agentId: d.agentId || undefined,
          name: d.label || undefined,
          instruction: d.instruction || undefined,
          mcpServers: d.mcpServers || undefined,
          enableTools: d.enableTools || undefined,
          outputKey: d.outputKey || undefined,
        };
      });
      return {
        agentId: currentAgentId || '',
        name: currentAgentName,
        engine,
        agentscopePipelineType: 'sequential',
        agentscopeAgents,
      };
    }

    // HYBRID: subEngines mapping
    let subEngines: Record<string, string> | undefined;
    if (engine === 'HYBRID') {
      subEngines = {};
      agentNodes.forEach((n) => {
        const d = n.data as any;
        if (d.subEngine === 'AGENTSCOPE') {
          subEngines![n.id] = 'AGENTSCOPE';
        }
      });
    }

    // GRAPH / HYBRID: 输出 graphNodes / graphEdges / graphStart
    return {
      agentId: currentAgentId || '',
      name: currentAgentName,
      engine,
      graphStart,
      graphNodes,
      graphEdges,
      subEngines,
    };
  },
}));
