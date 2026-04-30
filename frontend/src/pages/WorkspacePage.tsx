/**
 * 工作台页面
 * 画布区域（左）+ 节点编辑面板（左侧浮层）+ 聊天面板（右）
 */
import { useNavigate } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { ReactFlowProvider } from '@xyflow/react';
import AgentCanvas from '@/features/canvas/AgentCanvas';
import CanvasToolbar from '@/features/canvas/CanvasToolbar';
import NodeEditPanel from '@/features/canvas/NodeEditPanel';
import ChatPanel from '@/features/chat/ChatPanel';
import { useCanvasStore } from '@/stores/canvas';

function WorkspacePage() {
  const navigate = useNavigate();
  const selectedNodeId = useCanvasStore((s) => s.selectedNodeId);

  return (
    <div className="flex h-full relative">
      {/* 画布区域 */}
      <div className="flex-1 relative">
        <ReactFlowProvider>
          <CanvasToolbar />
          <AgentCanvas />
          {/* 切换简单模式按钮 */}
          <button
            onClick={() => navigate('/chat')}
            className="pointer-events-auto absolute right-4 top-4 z-10 text-sm text-slate-500 hover:text-slate-700 flex items-center gap-1 px-3 py-1.5 rounded-lg bg-white/90 border border-gray-200 shadow-sm backdrop-blur-sm"
          >
            切换简单模式 <ArrowRight size={14} />
          </button>
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
