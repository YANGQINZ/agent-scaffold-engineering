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
  /** 当前编辑的引擎类型 */
  currentEngineType: EngineType;

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
  setCurrentAgent: (agentId: string | null, name: string, engine: EngineType) => void;
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
  currentEngineType: 'GRAPH',

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

  setCurrentAgent: (currentAgentId, currentAgentName, currentEngineType) =>
    set({ currentAgentId, currentAgentName, currentEngineType }),

  loadFromAgentDefinition: (agent) => {
    const graphNodes: Node[] = (agent.graphNodes ?? []).map((n, idx) => ({
      id: n.id,
      type: (agent.graphStart ?? []).includes(n.id) ? 'start' : 'engine',
      position: { x: 250 + idx * 200, y: 150 + (idx % 2) * 120 },
      data: {
        label: n.id,
        agentId: n.agentId,
        reactAgentId: n.reactAgentId,
        ragEnabled: n.ragEnabled,
        knowledgeBaseId: n.knowledgeBaseId,
        engineType: agent.subEngines?.[n.id] ?? agent.engine,
      },
    }));

    const startIds = agent.graphStart ?? [];
    const startNode = graphNodes.find((n) => startIds.includes(n.id));
    if (!startNode && startIds.length > 0) {
      graphNodes.unshift({
        id: startIds[0],
        type: 'start',
        position: { x: 250, y: 100 },
        data: { label: '开始' },
      });
    }

    const graphEdges: Edge[] = (agent.graphEdges ?? []).map((e) => ({
      id: `e_${e.from}-${e.to}`,
      source: e.from,
      target: e.to,
      type: e.condition ? 'condition' : 'default',
      data: { condition: e.condition },
    }));

    set({
      nodes: graphNodes,
      edges: graphEdges,
      currentAgentId: agent.agentId,
      currentAgentName: agent.name,
      currentEngineType: agent.engine,
    });
  },

  exportToAgentDefinition: () => {
    const state = get();
    const { nodes, edges, currentAgentName, currentEngineType } = state;

    // graphStart 应为 start 节点指向的第一个实际节点，而非 start 节点本身
    const startNodes = nodes.filter((n) => n.type === 'start');
    const graphStart: string[] = startNodes
      .map((sn) => edges.find((e) => e.source === sn.id)?.target)
      .filter((t): t is string => !!t);
    if (graphStart.length === 0) {
      const first = nodes.find((n) => n.type !== 'start' && n.type !== 'end')?.id;
      if (first) graphStart.push(first);
    }

    const graphNodes = nodes
      .filter((n) => n.type !== 'start' && n.type !== 'end')
      .map((n) => ({
        id: n.id,
        agentId: n.data?.agentId as string | undefined,
        reactAgentId: n.data?.reactAgentId as string | undefined,
        ragEnabled: n.data?.ragEnabled as boolean | undefined,
        knowledgeBaseId: n.data?.knowledgeBaseId as string | undefined,
      }));

    // 导出边时排除引用 start/end 类型节点的边：
    // start 节点由后端 graphStart + addEdge(START, ...) 处理，
    // end 节点由后端叶子节点自动连 END 处理。
    const validNodeIds = new Set(graphNodes.map((n) => n.id));
    const graphEdges = edges
      .filter((e) => validNodeIds.has(e.source) && validNodeIds.has(e.target))
      .map((e) => ({
        from: e.source,
        to: e.target,
        condition: (e.data as Record<string, unknown> | undefined)?.condition as string | undefined,
      }));

    const subEngines: Record<string, EngineType> = {};
    nodes.forEach((n) => {
      const et = n.data?.engineType as EngineType | undefined;
      if (et && et !== currentEngineType) {
        subEngines[n.id] = et;
      }
    });

    return {
      name: currentAgentName || '未命名 Agent',
      engine: currentEngineType,
      graphStart,
      graphNodes,
      graphEdges,
      ...(Object.keys(subEngines).length > 0 ? { subEngines } : {}),
    };
  },
}));
