import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Cpu } from 'lucide-react';
import { useCanvasStore } from '../../../stores/canvas';

export interface AgentNodeData extends Record<string, unknown> {
  label: string;
  instruction?: string;
  subEngine?: 'GRAPH' | 'AGENTSCOPE';
  agentId?: string;
  ragEnabled?: boolean;
  knowledgeBaseId?: string;
  mcpServers?: import('../../../api/agent').McpServerConfig[];
}

const engineStyles: Record<string, { bg: string; border: string; badge: string; text: string }> = {
  GRAPH: {
    bg: 'bg-blue-50',
    border: 'border-blue-300',
    badge: 'bg-blue-500',
    text: 'text-blue-600',
  },
  AGENTSCOPE: {
    bg: 'bg-amber-50',
    border: 'border-amber-300',
    badge: 'bg-amber-500',
    text: 'text-amber-600',
  },
};

export function AgentNode({ id, data }: NodeProps) {
  const nodeStates = useCanvasStore((s) => s.nodeStates);
  const status = nodeStates[id]?.status ?? 'idle';
  const engine = data.subEngine || 'GRAPH';
  const style = engineStyles[engine] || engineStyles.GRAPH;
  const hasRag = data.ragEnabled;
  const hasMcp = data.mcpServers && data.mcpServers.length > 0;

  const statusColors: Record<string, string> = {
    idle: '',
    running: 'ring-2 ring-blue-400 animate-pulse',
    done: 'ring-2 ring-green-400',
    error: 'ring-2 ring-red-400',
  };

  return (
    <div
      className={`${style.bg} border ${style.border} rounded-lg shadow-sm min-w-[120px] max-w-[200px] ${statusColors[status]}`}
    >
      <div className={`${style.badge} text-white px-3 py-1 rounded-t-lg text-sm font-medium flex items-center gap-1.5`}>
        <Cpu size={14} />
        <span className="truncate">{data.label || 'Agent'}</span>
      </div>
      <div className="px-3 py-2 text-xs flex items-center gap-2">
        <span className={`${style.badge} text-white px-1.5 py-0.5 rounded text-[10px] font-bold`}>
          {engine}
        </span>
        {hasRag && (
          <span className="w-2 h-2 rounded-full bg-amber-400" title="RAG 启用" />
        )}
        {hasMcp && (
          <span className="w-2 h-2 rounded-full bg-green-400" title="MCP 配置" />
        )}
      </div>
      <Handle type="target" position={Position.Top} className={`${style.badge.replace('bg-', '!bg-')}`} />
      <Handle type="source" position={Position.Bottom} className={`${style.badge.replace('bg-', '!bg-')}`} />
    </div>
  );
}
