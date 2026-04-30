/**
 * 独立对话页面（专家模式）
 * 布局：顶部 Agent 选择器 → 左侧会话侧边栏 + 右侧聊天面板
 */
import { useEffect } from 'react';
import AgentSelector from '@/features/chat/AgentSelector';
import SessionSidebar from '@/features/chat/SessionSidebar';
import ChatPanel from '@/features/chat/ChatPanel';
import { useAgent } from '@/hooks/useAgent';

function ChatPage() {
  const { loadAgents } = useAgent();

  // 页面加载时获取 Agent 列表
  useEffect(() => {
    loadAgents();
  }, [loadAgents]);

  return (
    <div className="flex h-full flex-col">
      {/* ── 顶部：Agent 选择器 ── */}
      <AgentSelector />

      {/* ── 主体：左侧边栏 + 右侧聊天 ── */}
      <div className="flex flex-1 overflow-hidden">
        <SessionSidebar />
        <div className="flex-1">
          <ChatPanel />
        </div>
      </div>
    </div>
  );
}

export default ChatPage;
