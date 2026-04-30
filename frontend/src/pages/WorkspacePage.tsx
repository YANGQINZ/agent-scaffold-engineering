/**
 * 工作台页面
 * 画布区域（左）+ 节点编辑面板（左侧浮层）+ 聊天面板（右）
 */
import { ReactFlowProvider } from '@xyflow/react';
import AgentCanvas from '@/features/canvas/AgentCanvas';
import CanvasToolbar from '@/features/canvas/CanvasToolbar';
import NodeEditPanel from '@/features/canvas/NodeEditPanel';
import ChatPanel from '@/features/chat/ChatPanel';
import { useCanvasStore } from '@/stores/canvas';

function WorkspacePage() {
  const selectedNodeId = useCanvasStore((s) => s.selectedNodeId);

  return (
    <div className="flex h-full relative">
      {/* 画布区域 */}
      <div className="flex-1 relative">
        <ReactFlowProvider>
          <CanvasToolbar />
          <AgentCanvas />
        </ReactFlowProvider>

        {/* 节点编辑面板：左侧浮层 */}
        <div
          className={`absolute inset-y-0 left-0 z-10 transition-transform duration-200 ease-in-out ${
            selectedNodeId ? 'translate-x-0' : '-translate-x-full'
          }`}
        >
          <NodeEditPanel />
        </div>
      </div>

      {/* 聊天面板 */}
      <div className="w-[25%] min-w-[280px] border-l border-border">
        <ChatPanel />
      </div>
    </div>
  );
}

export default WorkspacePage;
