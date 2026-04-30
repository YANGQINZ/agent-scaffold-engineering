/**
 * 节点执行状态展示组件
 * 水平排列的节点状态胶囊：运行中（紫色脉冲）、完成（绿色对勾）、错误（红色叉号）
 */
import { memo } from 'react';
import { Check, X, Loader2 } from 'lucide-react';
import { useChatStore, type NodeExecutionStatus as NodeStatus } from '@/stores/chat';
import { cn } from '@/lib/utils';

/** 单个节点状态胶囊的样式映射 */
const statusStyles: Record<string, string> = {
  running:
    'bg-purple-100 text-purple-700 border-purple-300',
  done:
    'bg-green-100 text-green-700 border-green-300',
  error:
    'bg-red-100 text-red-700 border-red-300',
};

/** 单个节点状态图标 */
function StatusIcon({ status }: { status: NodeStatus['status'] }) {
  switch (status) {
    case 'running':
      return <Loader2 className="size-3 animate-spin" />;
    case 'done':
      return <Check className="size-3" />;
    case 'error':
      return <X className="size-3" />;
    default:
      return null;
  }
}

function NodeExecutionStatusView() {
  const nodeExecutionStatus = useChatStore((s) => s.nodeExecutionStatus);

  // 当有节点执行状态时显示（所有模式）
  if (nodeExecutionStatus.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-1.5 px-4 py-2 border-b border-gray-100 bg-gray-50/50">
      <span className="text-xs text-gray-500 mr-1">节点状态</span>
      {nodeExecutionStatus.map((node) => (
        <div
          key={node.nodeId}
          className={cn(
            'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium transition-colors',
            statusStyles[node.status] ?? 'bg-gray-100 text-gray-600 border-gray-300',
            node.status === 'running' && 'animate-pulse',
          )}
        >
          <StatusIcon status={node.status} />
          <span>{node.nodeId}</span>
          {node.duration !== undefined && (
            <span className="opacity-60">{node.duration}ms</span>
          )}
        </div>
      ))}
    </div>
  );
}

export default memo(NodeExecutionStatusView);
