/**
 * Agent 编排画布
 * 基于 @xyflow/react 的主画布组件，注册自定义节点和边类型
 */
import { memo, useCallback, useRef } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useReactFlow,
  type Connection,
  type Edge,
  type Node,
  BackgroundVariant,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { useCanvasStore } from '@/stores/canvas';
import StartNode from './nodes/StartNode';
import EndNode from './nodes/EndNode';
import ChatNode from './nodes/ChatNode';
import EngineNode from './nodes/EngineNode';
import DefaultEdge from './edges/DefaultEdge';
import ConditionEdge from './edges/ConditionEdge';

/** 注册自定义节点类型 */
const nodeTypes = {
  start: StartNode,
  end: EndNode,
  chat: ChatNode,
  engine: EngineNode,
};

/** 注册自定义边类型 */
const edgeTypes = {
  default: DefaultEdge,
  condition: ConditionEdge,
};

function AgentCanvas() {
  const {
    nodes,
    edges,
    onNodesChange,
    onEdgesChange,
    setNodes,
    setEdges,
    setSelectedNodeId,
  } = useCanvasStore();

  const { screenToFlowPosition } = useReactFlow();

  /** 追踪拖拽连线的起点 */
  const connectingFrom = useRef<{ nodeId: string | null; handleType: string | null; edgeCount: number }>({
    nodeId: null,
    handleType: null,
    edgeCount: 0,
  });

  /** 连线回调：添加默认边 */
  const onConnect = useCallback(
    (connection: Connection) => {
      if (!connection.source || !connection.target) return;
      const newEdge: Edge = {
        id: `e_${connection.source}-${connection.target}`,
        source: connection.source,
        target: connection.target,
        type: 'default',
      };
      setEdges([...edges, newEdge]);
    },
    [edges, setEdges],
  );

  /** 节点点击：更新选中节点 */
  const onNodeClick = useCallback(
    (_event: React.MouseEvent, node: { id: string }) => {
      setSelectedNodeId(node.id);
    },
    [setSelectedNodeId],
  );

  /** 画布空白点击：取消选中 */
  const onPaneClick = useCallback(() => {
    setSelectedNodeId(null);
  }, [setSelectedNodeId]);

  /** 连线开始：记录起点 */
  const onConnectStart = useCallback(
    (_event: React.MouseEvent | React.TouchEvent, { nodeId, handleType }: { nodeId: string | null; handleType: string | null }) => {
      connectingFrom.current = {
        nodeId: handleType === 'source' ? nodeId : null,
        handleType,
        edgeCount: edges.length,
      };
    },
    [edges.length],
  );

  /** 连线结束：如果拖到空白区域则自动添加 ChatNode */
  const onConnectEnd = useCallback(
    (event: MouseEvent | TouchEvent) => {
      const { nodeId: sourceNodeId, handleType, edgeCount } = connectingFrom.current;

      // 重置追踪状态
      connectingFrom.current = { nodeId: null, handleType: null, edgeCount: 0 };

      // 仅处理从 source handle 拖出
      if (handleType !== 'source' || !sourceNodeId) return;

      // 如果已有新 edge 产生（连到了有效 target），不创建新节点
      if (edges.length > edgeCount) return;

      // 将鼠标屏幕坐标转为画布坐标
      const screenPoint = 'clientX' in event
        ? { x: event.clientX, y: event.clientY }
        : { x: 0, y: 0 };
      const flowPosition = screenToFlowPosition(screenPoint);

      // 创建新 ChatNode
      const newNodeId = `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
      const newNode: Node = {
        id: newNodeId,
        type: 'chat',
        position: flowPosition,
        data: { label: '对话节点', engine: 'CHAT' },
      };

      // 创建从源节点到新节点的边
      const newEdge: Edge = {
        id: `e_${sourceNodeId}-${newNodeId}`,
        source: sourceNodeId,
        target: newNodeId,
        type: 'default',
      };

      setNodes([...nodes, newNode]);
      setEdges([...edges, newEdge]);
    },
    [nodes, edges, setNodes, setEdges, screenToFlowPosition],
  );

  return (
    <div className="relative h-full w-full">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onConnectStart={onConnectStart}
        onConnectEnd={onConnectEnd}
        onNodeClick={onNodeClick}
        onPaneClick={onPaneClick}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        defaultEdgeOptions={{ type: 'default', markerEnd: { type: 'arrowclosed' } }}
        className="bg-gray-50"
      >
        <Background variant={BackgroundVariant.Dots} gap={16} size={1} color="#e2e8f0" />
        <Controls
          showInteractive={false}
          className="!rounded-lg !border-gray-200 !shadow-sm"
        />
        <MiniMap
          nodeStrokeWidth={3}
          zoomable
          pannable
          className="!rounded-lg !border-gray-200 !shadow-sm"
        />
      </ReactFlow>
    </div>
  );
}

export default memo(AgentCanvas);
