/**
 * 对话 / 简单 Agent 节点
 * 蓝色卡片，显示引擎类型和 RAG 指示器
 */
import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { MessageSquare } from 'lucide-react';
import { useCanvasStore } from '@/stores/canvas';
import { cn } from '@/lib/utils';
import type { AgentDefinition } from '@/api/agent';

/** ChatNode 自定义数据 */
export interface ChatNodeData extends Record<string, unknown> {
  label: string;
  agentId?: string;
  agentName?: string;
  engine?: string;
  ragEnabled?: boolean;
}

const engineBadgeColors: Record<string, string> = {
  CHAT: 'bg-blue-100 text-blue-700',
  GRAPH: 'bg-purple-100 text-purple-700',
  AGENTSCOPE: 'bg-amber-100 text-amber-700',
  HYBRID: 'bg-rose-100 text-rose-700',
};

function ChatNode({ id, data, selected }: NodeProps) {
  const { label, agentName, engine, ragEnabled } = data as ChatNodeData;
  const nodeStates = useCanvasStore((s) => s.nodeStates);
  const status = nodeStates[id]?.status;

  const badgeClass = engine
    ? engineBadgeColors[engine] ?? 'bg-gray-100 text-gray-700'
    : 'bg-blue-100 text-blue-700';

  return (
    <div
      className={cn(
        'min-w-[160px] rounded-lg border border-blue-200 bg-white shadow-sm transition-all',
        selected && 'ring-2 ring-blue-400 ring-offset-1',
        status === 'running' && 'animate-pulse ring-2 ring-purple-400 ring-offset-1',
        status === 'done' && 'border-green-300',
        status === 'error' && 'border-red-300',
      )}
    >
      {/* Header */}
      <div className="flex items-center gap-2 rounded-t-lg bg-blue-50 px-3 py-2">
        <MessageSquare className="size-4 text-blue-500" />
        <span className="text-sm font-medium text-gray-800">{label}</span>
        {agentName && (
          <span className="ml-auto text-xs text-gray-400">{agentName}</span>
        )}
      </div>

      {/* Body */}
      <div className="flex items-center gap-2 px-3 py-2">
        {engine && (
          <span
            className={cn(
              'inline-flex items-center rounded-md px-1.5 py-0.5 text-xs font-medium',
              badgeClass,
            )}
          >
            {engine}
          </span>
        )}
        {ragEnabled && (
          <span className="inline-flex items-center gap-1 text-xs text-amber-600">
            <span className="size-2 rounded-full bg-amber-400" />
            RAG
          </span>
        )}
      </div>

      {/* Handles */}
      <Handle
        type="target"
        position={Position.Top}
        className="!size-3 !border-2 !border-blue-300 !bg-white"
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="!size-3 !border-2 !border-blue-300 !bg-white"
      />
    </div>
  );
}

export default memo(ChatNode);
