/**
 * Agent 选择器（顶部横栏）
 * 显示所有可用 Agent 为胶囊按钮，点击切换当前 Agent
 * 切换时更新 chatStore.selectedAgentId，会话列表由 SessionSidebar 统一加载
 */
import { memo, useCallback, useEffect } from 'react';
import { Bot } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { useChatStore } from '@/stores/chat';
import { useCanvasStore } from '@/stores/canvas';
import { cn } from '@/lib/utils';

/** 引擎类型显示名 */
const engineLabel: Record<string, string> = {
  CHAT: 'Chat',
  GRAPH: 'Graph',
  AGENTSCOPE: 'AS',
  HYBRID: 'Hybrid',
};

function AgentSelector() {
  const agents = useCanvasStore((s) => s.agents);
  const selectedAgentId = useChatStore((s) => s.selectedAgentId);
  const setSelectedAgentId = useChatStore((s) => s.setSelectedAgentId);
  const clearMessages = useChatStore((s) => s.clearMessages);
  const setActiveSessionId = useChatStore((s) => s.setActiveSessionId);

  /** 切换 Agent：更新选中 ID，清空消息 */
  const handleSelectAgent = useCallback(
    (agentId: string) => {
      if (agentId === selectedAgentId) return;

      setSelectedAgentId(agentId);
      clearMessages();
      setActiveSessionId(null);
    },
    [selectedAgentId, setSelectedAgentId, clearMessages, setActiveSessionId],
  );

  /** 首次加载时自动选中第一个 Agent */
  useEffect(() => {
    if (!selectedAgentId && agents.length > 0) {
      handleSelectAgent(agents[0].agentId);
    }
  }, [selectedAgentId, agents, handleSelectAgent]);

  if (agents.length === 0) {
    return (
      <div className="flex items-center justify-center border-b border-gray-200 px-4 py-3 text-sm text-gray-400">
        暂无可用 Agent
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2 overflow-x-auto border-b border-gray-200 px-4 py-2.5">
      {agents.map((agent) => {
        const isActive = agent.agentId === selectedAgentId;
        return (
          <button
            key={agent.agentId}
            onClick={() => handleSelectAgent(agent.agentId)}
            className={cn(
              'inline-flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-medium transition-colors',
              isActive
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200',
            )}
          >
            <Bot className="size-3.5" />
            <span className="max-w-[120px] truncate">{agent.name}</span>
            <Badge
              className={cn(
                'h-4 rounded-full px-1.5 text-[10px] font-semibold leading-none',
                isActive
                  ? 'border-indigo-400 bg-indigo-500 text-indigo-100'
                  : 'border-gray-300 bg-gray-50 text-gray-500',
              )}
              variant="outline"
            >
              {engineLabel[agent.engine] ?? agent.engine}
            </Badge>
          </button>
        );
      })}
    </div>
  );
}

export default memo(AgentSelector);
