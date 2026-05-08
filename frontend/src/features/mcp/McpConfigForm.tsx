/**
 * MCP 配置管理表单弹窗
 * 用于新增/编辑持久化的 MCP Server 配置（含 description 字段）
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
import type { McpConfigItem } from '@/api/mcp';

/** 传输类型选项 */
const TRANSPORT_OPTIONS = [
  { value: 'stdio', label: 'stdio' },
  { value: 'sse', label: 'sse' },
  { value: 'streamableHttp', label: 'streamable-http' },
] as const;

/** HTTP 类型传输 */
const HTTP_TRANSPORTS = new Set(['sse', 'streamableHttp']);

/** 键值对（用于 headers） */
interface KeyValuePair {
  key: string;
  value: string;
}

interface McpConfigFormProps {
  /** 是否打开 */
  open: boolean;
  /** 关闭回调 */
  onOpenChange: (open: boolean) => void;
  /** 编辑模式初始数据 */
  initialData?: McpConfigItem;
  /** 提交回调 */
  onSubmit: (data: Omit<McpConfigItem, 'id'>) => void;
}

function McpConfigForm({ open, onOpenChange, initialData, onSubmit }: McpConfigFormProps) {
  const isEdit = !!initialData;

  const [name, setName] = useState('');
  const [transport, setTransport] = useState('stdio');
  const [command, setCommand] = useState('');
  const [argsText, setArgsText] = useState('');
  const [url, setUrl] = useState('');
  const [headers, setHeaders] = useState<KeyValuePair[]>([]);
  const [description, setDescription] = useState('');

  /** 初始化/重置表单 */
  const resetForm = useCallback(() => {
    if (initialData) {
      setName(initialData.name);
      setTransport(initialData.transport);
      setCommand(initialData.command ?? '');
      setArgsText(initialData.args?.join(', ') ?? '');
      setUrl(initialData.url ?? '');
      setDescription(initialData.description ?? '');
      setHeaders(
        initialData.headers
          ? Object.entries(initialData.headers).map(([key, value]) => ({ key, value }))
          : [],
      );
    } else {
      setName('');
      setTransport('stdio');
      setCommand('');
      setArgsText('');
      setUrl('');
      setDescription('');
      setHeaders([]);
    }
  }, [initialData]);

  useEffect(() => {
    if (open) resetForm();
  }, [open, resetForm]);

  const addHeader = useCallback(() => {
    setHeaders((prev) => [...prev, { key: '', value: '' }]);
  }, []);

  const removeHeader = useCallback((index: number) => {
    setHeaders((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const updateHeader = useCallback((index: number, field: 'key' | 'value', val: string) => {
    setHeaders((prev) =>
      prev.map((item, i) => (i === index ? { ...item, [field]: val } : item)),
    );
  }, []);

  const handleSubmit = useCallback(() => {
    if (!name.trim()) return;

    const data: Omit<McpConfigItem, 'id'> = {
      name: name.trim(),
      transport,
      description: description.trim() || undefined,
    };

    if (transport === 'stdio') {
      if (command.trim()) data.command = command.trim();
      const parsedArgs = argsText.split(',').map((a) => a.trim()).filter(Boolean);
      if (parsedArgs.length > 0) data.args = parsedArgs;
    }

    if (HTTP_TRANSPORTS.has(transport)) {
      if (url.trim()) data.url = url.trim();
      const headerRecord: Record<string, string> = {};
      for (const h of headers) {
        if (h.key.trim() && h.value.trim()) {
          headerRecord[h.key.trim()] = h.value.trim();
        }
      }
      if (Object.keys(headerRecord).length > 0) data.headers = headerRecord;
    }

    onSubmit(data);
    onOpenChange(false);
  }, [name, transport, command, argsText, url, headers, description, onSubmit, onOpenChange]);

  const isHttp = HTTP_TRANSPORTS.has(transport);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? '编辑 MCP 配置' : '新增 MCP 配置'}</DialogTitle>
          <DialogDescription>
            {isEdit ? '修改 MCP Server 连接配置' : '创建可复用的 MCP Server 配置'}
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-3 py-2">
          {/* 名称 */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-gray-700">名称</label>
            <Input
              placeholder="配置名称（唯一标识）"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isEdit}
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
                  placeholder="逗号分隔，如 -y, @modelcontextprotocol/server-memory"
                  value={argsText}
                  onChange={(e) => setArgsText(e.target.value)}
                />
              </div>
            </>
          )}

          {/* HTTP 字段 */}
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
                  <label className="text-sm font-medium text-gray-700">Headers</label>
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
                    <Input placeholder="Key" value={h.key} onChange={(e) => updateHeader(i, 'key', e.target.value)} className="flex-1" />
                    <Input placeholder="Value" value={h.value} onChange={(e) => updateHeader(i, 'value', e.target.value)} className="flex-1" />
                    <Button variant="ghost" size="icon-xs" onClick={() => removeHeader(i)}>
                      <Trash2 className="size-3 text-gray-400" />
                    </Button>
                  </div>
                ))}
              </div>
            </>
          )}

          {/* 描述 */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-gray-700">描述</label>
            <Input
              placeholder="配置描述（可选）"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleSubmit} disabled={!name.trim()}>
            {isEdit ? '保存' : '创建'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default memo(McpConfigForm);
