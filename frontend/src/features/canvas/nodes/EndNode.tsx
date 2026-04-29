/**
 * 结束节点
 * 红色圆角药丸形状，顶部一个 target Handle
 */
import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Square } from 'lucide-react';
import { useCanvasStore } from '@/stores/canvas';
import { cn } from '@/lib/utils';

function EndNode({ id, selected }: NodeProps) {
  const nodeStates = useCanvasStore((s) => s.nodeStates);
  const status = nodeStates[id]?.status;

  return (
    <div
      className={cn(
        'flex items-center gap-1.5 rounded-full bg-red-500 px-4 py-1.5 text-sm font-medium text-white shadow-sm transition-all',
        selected && 'ring-2 ring-red-400 ring-offset-2',
        status === 'running' && 'animate-pulse ring-2 ring-purple-400 ring-offset-2',
        status === 'done' && 'bg-red-600',
        status === 'error' && 'bg-red-700',
      )}
    >
      <Square className="size-3.5" />
      <span>结束</span>
      <Handle
        type="target"
        position={Position.Top}
        className="!size-3 !border-2 !border-red-500 !bg-white"
      />
    </div>
  );
}

export default memo(EndNode);
