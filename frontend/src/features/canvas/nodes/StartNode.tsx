/**
 * 开始节点
 * 绿色圆角药丸形状，底部一个 source Handle
 */
import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Play } from 'lucide-react';
import { useCanvasStore } from '@/stores/canvas';
import { cn } from '@/lib/utils';

function StartNode({ id, selected }: NodeProps) {
  const nodeStates = useCanvasStore((s) => s.nodeStates);
  const status = nodeStates[id]?.status;

  return (
    <div
      className={cn(
        'flex items-center gap-1.5 rounded-full bg-emerald-500 px-4 py-1.5 text-sm font-medium text-white shadow-sm transition-all',
        selected && 'ring-2 ring-emerald-400 ring-offset-2',
        status === 'running' && 'animate-pulse ring-2 ring-purple-400 ring-offset-2',
        status === 'done' && 'bg-emerald-600',
        status === 'error' && 'bg-red-500',
      )}
    >
      <Play className="size-3.5" />
      <span>开始</span>
      <Handle
        type="source"
        position={Position.Bottom}
        className="!size-3 !border-2 !border-emerald-500 !bg-white"
      />
    </div>
  );
}

export default memo(StartNode);
