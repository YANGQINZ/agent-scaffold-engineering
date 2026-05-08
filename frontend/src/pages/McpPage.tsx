/**
 * MCP Server 配置管理页面
 * 展示所有持久化的 MCP 配置，支持新增、编辑、删除
 */
import { memo, useCallback, useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, Server } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import {
  listMcpConfigs,
  createMcpConfig,
  updateMcpConfig,
  deleteMcpConfig,
  type McpConfigItem,
} from '@/api/mcp';
import McpConfigForm from '@/features/mcp/McpConfigForm';

function McpPage() {
  const [configs, setConfigs] = useState<McpConfigItem[]>([]);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<McpConfigItem | undefined>();

  /** 加载配置列表 */
  const loadConfigs = useCallback(async () => {
    try {
      const data = await listMcpConfigs();
      setConfigs(data);
    } catch {
      console.error('加载MCP配置列表失败');
    }
  }, []);

  useEffect(() => {
    loadConfigs();
  }, [loadConfigs]);

  /** 新增 */
  const handleAdd = useCallback(() => {
    setEditing(undefined);
    setFormOpen(true);
  }, []);

  /** 编辑 */
  const handleEdit = useCallback((config: McpConfigItem) => {
    setEditing(config);
    setFormOpen(true);
  }, []);

  /** 删除 */
  const handleDelete = useCallback(
    async (id: number) => {
      await deleteMcpConfig(id);
      loadConfigs();
    },
    [loadConfigs],
  );

  /** 表单提交 */
  const handleFormSubmit = useCallback(
    async (data: Omit<McpConfigItem, 'id'>) => {
      if (editing) {
        await updateMcpConfig(editing.id, data);
      } else {
        await createMcpConfig(data);
      }
      loadConfigs();
    },
    [editing, loadConfigs],
  );

  return (
    <div className="flex h-full flex-col">
      {/* 顶栏 */}
      <div className="flex h-14 shrink-0 items-center justify-between border-b border-gray-200 px-6">
        <div className="flex items-center gap-2">
          <Server className="size-4 text-green-600" />
          <span className="text-sm font-semibold text-gray-900">MCP 配置管理</span>
        </div>
        <Button size="sm" onClick={handleAdd}>
          <Plus className="mr-1 size-4" />
          新增配置
        </Button>
      </div>

      <Separator />

      {/* 配置列表 */}
      <div className="flex-1 overflow-y-auto p-6">
        {configs.length === 0 && (
          <div className="mt-20 text-center text-gray-400">
            <Server className="mx-auto mb-2 size-8 text-gray-300" />
            <p>暂无 MCP 配置</p>
            <p className="mt-1 text-xs">点击"新增配置"添加 MCP Server</p>
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {configs.map((config) => (
            <div
              key={config.id}
              className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md"
            >
              <div className="flex items-start justify-between">
                <div className="min-w-0 flex-1">
                  <h3 className="truncate text-sm font-semibold text-gray-900">
                    {config.name}
                  </h3>
                  <span className="mt-0.5 inline-block rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-500">
                    {config.transport}
                  </span>
                </div>
                <div className="flex items-center gap-1">
                  <Button variant="ghost" size="icon-xs" onClick={() => handleEdit(config)}>
                    <Pencil className="size-3 text-gray-400" />
                  </Button>
                  <Button variant="ghost" size="icon-xs" onClick={() => handleDelete(config.id)}>
                    <Trash2 className="size-3 text-red-400" />
                  </Button>
                </div>
              </div>

              {config.description && (
                <p className="mt-2 text-xs text-gray-500">{config.description}</p>
              )}

              <div className="mt-2 text-xs text-gray-400">
                {config.transport === 'stdio'
                  ? `${config.command ?? ''} ${(config.args ?? []).join(' ')}`
                  : config.url ?? ''}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 配置表单弹窗 */}
      <McpConfigForm
        open={formOpen}
        onOpenChange={setFormOpen}
        initialData={editing}
        onSubmit={handleFormSubmit}
      />
    </div>
  );
}

export default memo(McpPage);
