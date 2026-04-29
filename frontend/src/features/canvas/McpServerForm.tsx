/**
 * MCP Server 配置对话框
 * 支持创建和编辑三种传输类型的 MCP Server：stdio / sse / streamable-http
 */
import { memo, useCallback, useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import type { McpServerConfig } from '@/api/agent';

// ═══════════════════════════════════════════════════════════
// 类型定义
// ═══════════════════════════════════════════════════════════

/** 传输类型选项 */
const TRANSPORT_OPTIONS = [
  { value: 'stdio', label: 'stdio' },
  { value: 'sse', label: 'sse' },
  { value: 'streamableHttp', label: 'streamable-http' },
] as const;

/** HTTP 类型传输集合 */
const HTTP_TRANSPORTS = new Set(['sse', 'streamableHttp']);

/** 键值对（用于 headers） */
interface KeyValuePair {
  key: string;
  value: string;
}

interface McpServerFormProps {
  /** 是否打开对话框 */
  open: boolean;
  /** 关闭对话框回调 */
  onOpenChange: (open: boolean) => void;
  /** 编辑模式传入的初始数据，不传则为新建模式 */
  initialData?: McpServerConfig;
  /** 提交回调 */
  onSubmit: (config: McpServerConfig) => void;
}

// ═══════════════════════════════════════════════════════════
// 组件
// ═══════════════════════════════════════════════════════════

function McpServerForm({
  open,
  onOpenChange,
  initialData,
  onSubmit,
}: McpServerFormProps) {
  const isEdit = !!initialData;

  // ─── 表单状态 ───
  const [name, setName] = useState('');
  const [transport, setTransport] = useState('stdio');
  const [command, setCommand] = useState('');
  const [argsText, setArgsText] = useState('');
  const [url, setUrl] = useState('');
  const [headers, setHeaders] = useState<KeyValuePair[]>([]);

  /** 初始化 / 重置表单 */
  const resetForm = useCallback(() => {
    if (initialData) {
      setName(initialData.name);
      setTransport(initialData.transport);
      setCommand(initialData.command ?? '');
      setArgsText(initialData.args?.join(', ') ?? '');
      setUrl(initialData.url ?? '');
      setHeaders(
        initialData.headers
          ? Object.entries(initialData.headers).map(([key, value]) => ({
              key,
              value,
            }))
          : [],
      );
    } else {
      setName('');
      setTransport('stdio');
      setCommand('');
      setArgsText('');
      setUrl('');
      setHeaders([]);
    }
  }, [initialData]);

  useEffect(() => {
    if (open) resetForm();
  }, [open, resetForm]);

  /** 添加 header 键值对 */
  const addHeader = useCallback(() => {
    setHeaders((prev) => [...prev, { key: '', value: '' }]);
  }, []);

  /** 删除 header 键值对 */
  const removeHeader = useCallback((index: number) => {
    setHeaders((prev) => prev.filter((_, i) => i !== index));
  }, []);

  /** 更新 header 键值对 */
  const updateHeader = useCallback(
    (index: number, field: 'key' | 'value', val: string) => {
      setHeaders((prev) =>
        prev.map((item, i) => (i === index ? { ...item, [field]: val } : item)),
      );
    },
    [],
  );

  /** 提交表单 */
  const handleSubmit = useCallback(() => {
    if (!name.trim()) return;

    const config: McpServerConfig = {
      name: name.trim(),
      transport,
    };

    if (transport === 'stdio') {
      if (command.trim()) config.command = command.trim();
      const parsedArgs = argsText
        .split(',')
        .map((a) => a.trim())
        .filter(Boolean);
      if (parsedArgs.length > 0) config.args = parsedArgs;
    }

    if (HTTP_TRANSPORTS.has(transport)) {
      if (url.trim()) config.url = url.trim();
      const headerRecord: Record<string, string> = {};
      for (const h of headers) {
        if (h.key.trim() && h.value.trim()) {
          headerRecord[h.key.trim()] = h.value.trim();
        }
      }
      if (Object.keys(headerRecord).length > 0) config.headers = headerRecord;
    }

    onSubmit(config);
    onOpenChange(false);
  }, [name, transport, command, argsText, url, headers, onSubmit, onOpenChange]);

  const isHttp = HTTP_TRANSPORTS.has(transport);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? '编辑 MCP Server' : '添加 MCP Server'}</DialogTitle>
          <DialogDescription>
            {isEdit ? '修改 MCP Server 传输配置' : '配置新的 MCP Server 连接'}
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-3 py-2">
          {/* 名称 */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-gray-700">名称</label>
            <Input
              placeholder="MCP Server 名称（唯一标识）"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          {/* 传输类型 */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-gray-700">传输类型</label>
            <Select value={transport} onValueChange={setTransport}>
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {TRANSPORT_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* stdio 字段 */}
          {transport === 'stdio' && (
            <>
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-gray-700">命令</label>
                <Input
                  placeholder="可执行命令，如 npx、python"
                  value={command}
                  onChange={(e) => setCommand(e.target.value)}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-gray-700">参数</label>
                <Input
                  placeholder="逗号分隔的参数，如 -y, @modelcontextprotocol/server-memory"
                  value={argsText}
                  onChange={(e) => setArgsText(e.target.value)}
                />
              </div>
            </>
          )}

          {/* HTTP 字段（sse / streamable-http） */}
          {isHttp && (
            <>
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-gray-700">URL</label>
                <Input
                  placeholder="http://localhost:3000/sse"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-sm font-medium text-gray-700">
                    Headers
                  </label>
                  <Button variant="ghost" size="xs" onClick={addHeader}>
                    <Plus className="size-3" />
                    添加
                  </Button>
                </div>
                {headers.length === 0 && (
                  <p className="text-xs text-gray-400">暂无自定义 Headers</p>
                )}
                {headers.map((h, i) => (
                  <div key={i} className="flex items-center gap-1.5">
                    <Input
                      placeholder="Key"
                      value={h.key}
                      onChange={(e) => updateHeader(i, 'key', e.target.value)}
                      className="flex-1"
                    />
                    <Input
                      placeholder="Value"
                      value={h.value}
                      onChange={(e) => updateHeader(i, 'value', e.target.value)}
                      className="flex-1"
                    />
                    <Button
                      variant="ghost"
                      size="icon-xs"
                      onClick={() => removeHeader(i)}
                    >
                      <Trash2 className="size-3 text-gray-400" />
                    </Button>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleSubmit} disabled={!name.trim()}>
            {isEdit ? '保存' : '添加'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default memo(McpServerForm);
