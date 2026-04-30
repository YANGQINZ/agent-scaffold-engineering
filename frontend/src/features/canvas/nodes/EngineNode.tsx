/**
 * 引擎节点 (Graph / AgentScope / Hybrid)
 * 紫色卡片，显示引擎类型、简要信息和 MCP 指示器
 */
import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Cpu } from 'lucide-react';
import { useCanvasStore } from '@/stores/canvas';
import { cn } from '@/lib/utils';

/** EngineNode 自定义数据 */
export interface EngineNodeData extends Record<string, unknown> {
  label: string;
  engineType: string;
  /** 简要描述：如 pipeline 类型、节点数量等 */
  info?: string;
  /** 是否配置了 MCP Server */
  hasMcp?: boolean;
}

const engineTypeColors: Record<string, string> = {
  GRAPH: 'bg-purple-100 text-purple-700',
  AGENTSCOPE: 'bg-amber-100 text-amber-700',
  HYBRID: 'bg-rose-100 text-rose-700',
};

const engineTypeBorderColors: Record<string, string> = {
  GRAPH: 'border-purple-200',
  AGENTSCOPE: 'border-amber-200',
  HYBRID: 'border-rose-200',
};

const engineTypeHeaderColors: Record<string, string> = {
  GRAPH: 'bg-purple-50',
  AGENTSCOPE: 'bg-amber-50',
  HYBRID: 'bg-rose-50',
};

function EngineNode({ id, data, selected }: NodeProps) {
  const { label, engineType, info, hasMcp } = data as EngineNodeData;
  const nodeStates = useCanvasStore((s) => s.nodeStates);
  const status = nodeStates[id]?.status;

  const badgeClass =
    engineTypeColors[engineType] ?? 'bg-gray-100 text-gray-700';

  const borderClass = engineTypeBorderColors[engineType] ?? 'border-gray-200';
  const headerClass = engineTypeHeaderColors[engineType] ?? 'bg-gray-50';

  return (
    <div
      className={cn(
        'min-w-[180px] rounded-lg border bg-white shadow-sm transition-all',
        borderClass,
        selected && 'ring-2 ring-offset-1',
        selected && engineType === 'GRAPH' && 'ring-purple-400',
        selected && engineType === 'AGENTSCOPE' && 'ring-amber-400',
        selected && engineType === 'HYBRID' && 'ring-rose-400',
        status === 'running' && 'animate-pulse ring-2 ring-offset-1',
        status === 'running' && engineType === 'GRAPH' && 'ring-purple-500',
        status === 'running' && engineType === 'AGENTSCOPE' && 'ring-amber-500',
        status === 'running' && engineType === 'HYBRID' && 'ring-rose-500',
        status === 'done' && 'border-green-300',
        status === 'error' && 'border-red-300',
      )}
    >
      {/* Header */}
      <div className={cn('flex items-center gap-2 rounded-t-lg px-3 py-2', headerClass)}>
        <Cpu className={cn(
          'size-4',
          engineType === 'GRAPH' && 'text-purple-500',
          engineType === 'AGENTSCOPE' && 'text-amber-500',
          engineType === 'HYBRID' && 'text-rose-500',
        )} />
        <span className="text-sm font-medium text-gray-800">{label}</span>
        <span
          className={cn(
            'ml-auto inline-flex items-center rounded-md px-1.5 py-0.5 text-xs font-medium',
            badgeClass,
          )}
        >
          {engineType}
        </span>
      </div>

      {/* Body */}
      <div className="flex items-center gap-2 px-3 py-2">
        {info && (
          <span className="text-xs text-gray-500">{info}</span>
        )}
        {hasMcp && (
          <span className="ml-auto inline-flex items-center gap-1 text-xs text-emerald-600">
            <span className="size-2 rounded-full bg-emerald-400" />
            MCP
          </span>
        )}
      </div>

      {/* Handles */}
      <Handle
        type="target"
        position={Position.Top}
        className={cn(
          '!size-3 !border-2 !bg-white',
          engineType === 'GRAPH' && '!border-purple-300',
          engineType === 'AGENTSCOPE' && '!border-amber-300',
          engineType === 'HYBRID' && '!border-rose-300',
        )}
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className={cn(
          '!size-3 !border-2 !bg-white',
          engineType === 'GRAPH' && '!border-purple-300',
          engineType === 'AGENTSCOPE' && '!border-amber-300',
          engineType === 'HYBRID' && '!border-rose-300',
        )}
      />
    </div>
  );
}

export default memo(EngineNode);
