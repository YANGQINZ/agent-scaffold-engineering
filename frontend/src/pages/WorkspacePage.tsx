/**
 * 工作台页面
 * 三栏布局：画布区域（左侧）+ 节点编辑面板（中间，选中节点时显示）+ 聊天面板（右侧）
 */
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
      <div className={`flex-1 relative ${selectedNodeId ? 'w-3/5' : 'w-4/5'}`}>
        <CanvasToolbar />
        <AgentCanvas />
      </div>

      {/* 节点编辑面板（仅选中节点时显示） */}
      {selectedNodeId && (
        <div className="w-[320px] border-l border-border overflow-y-auto bg-background">
          <NodeEditPanel />
        </div>
      )}

      {/* 聊天面板 */}
      <div className="w-[25%] min-w-[280px] border-l border-border">
        <ChatPanel />
      </div>
    </div>
  );
}

export default WorkspacePage;
