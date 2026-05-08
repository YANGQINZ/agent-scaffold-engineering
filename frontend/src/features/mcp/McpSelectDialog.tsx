/**
 * MCP 配置选择对话框
 * 展示已持久化的 MCP 配置列表，供用户选择后自动填充到节点属性
 */
import { memo, useCallback, useEffect, useState } from 'react';
import { Server } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { listMcpConfigs, type McpConfigItem } from '@/api/mcp';
import type { McpServerConfig } from '@/api/agent';

interface McpSelectDialogProps {
  /** 是否打开 */
  open: boolean;
  /** 关闭回调 */
  onOpenChange: (open: boolean) => void;
  /** 选择回调，返回 McpServerConfig 格式供节点属性使用 */
  onSelect: (config: McpServerConfig) => void;
}

function McpSelectDialog({ open, onOpenChange, onSelect }: McpSelectDialogProps) {
  const [configs, setConfigs] = useState<McpConfigItem[]>([]);

  useEffect(() => {
    if (open) {
      listMcpConfigs().then(setConfigs).catch(() => {});
    }
  }, [open]);

  const handleSelect = useCallback(
    (config: McpConfigItem) => {
      onSelect({
        name: config.name,
        transport: config.transport,
        command: config.command,
        args: config.args,
        url: config.url,
        headers: config.headers,
      });
      onOpenChange(false);
    },
    [onSelect, onOpenChange],
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>从配置库选择</DialogTitle>
          <DialogDescription>选择已保存的 MCP 配置自动填充到当前节点</DialogDescription>
        </DialogHeader>

        <div className="max-h-80 overflow-y-auto py-2">
          {configs.length === 0 && (
            <div className="py-8 text-center text-gray-400">
              <Server className="mx-auto mb-2 size-6 text-gray-300" />
              <p className="text-sm">暂无已保存的 MCP 配置</p>
              <p className="mt-1 text-xs">请先在 MCP 管理页面创建配置</p>
            </div>
          )}

          {configs.map((config) => (
            <button
              key={config.id}
              className="flex w-full items-center gap-3 rounded-md border border-gray-100 px-3 py-2.5 text-left transition-colors hover:bg-indigo-50"
              onClick={() => handleSelect(config)}
            >
              <Server className="size-4 shrink-0 text-green-500" />
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium text-gray-800">{config.name}</div>
                <div className="text-xs text-gray-400">
                  {config.transport === 'stdio'
                    ? `${config.command ?? ''} ${(config.args ?? []).join(' ')}`
                    : config.url ?? config.transport}
                </div>
              </div>
              <Button variant="outline" size="xs">
                选择
              </Button>
            </button>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}

export default memo(McpSelectDialog);
