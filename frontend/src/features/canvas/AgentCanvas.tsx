/**
 * Agent 编排画布
 * 基于 @xyflow/react 的主画布组件，注册自定义节点和边类型
 */
import { memo, useCallback } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type Connection,
  type Edge,
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
    setEdges,
    setSelectedNodeId,
  } = useCanvasStore();

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

  return (
    <div className="relative h-full w-full">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
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
