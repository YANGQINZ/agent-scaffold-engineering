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
import type { AgentDefinition } from '@/api/agent';

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
}

export const useCanvasStore = create<CanvasState>((set) => ({
  nodes: [],
  edges: [],
  selectedNodeId: null,
  agents: [],
  nodeStates: {},

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
}));
