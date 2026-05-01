/**
 * 画布顶部工具栏
 * 包含：添加节点（专家模式）、保存、导出 YAML（专家模式）
 */
import { memo, useCallback, useState } from 'react';
import {
  Plus,
  Save,
  FileDown,
  Cpu,
  CircleDot,
  ChevronDown,
  Loader2,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAppStore } from '@/stores/app';
import { useCanvasStore } from '@/stores/canvas';
import { saveOrUpdateAgent } from '@/api/agent';

function CanvasToolbar() {
  const mode = useAppStore((s) => s.mode);
  const {
    nodes,
    setNodes,
    currentAgentId,
    currentAgentName,
    setCurrentAgent,
    exportToAgentDefinition,
  } = useCanvasStore();

  const [addMenuOpen, setAddMenuOpen] = useState(false);
  const [saveDialogOpen, setSaveDialogOpen] = useState(false);
  const [saveLoading, setSaveLoading] = useState(false);
  const [draftName, setDraftName] = useState('');

  /** 生成唯一 ID */
  const genId = () =>
    `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

  /** 添加节点 */
  const addNode = useCallback(
    (type: string) => {
      const offset = nodes.length * 40;
      const defaultData: Record<string, any> = {
        label: type === 'start' ? '开始' : type === 'end' ? '结束' : 'Agent 节点',
      };
      if (type === 'agent') {
        defaultData.subEngine = 'GRAPH';
      }
      const newNode = {
        id: genId(),
        type,
        position: { x: 250 + offset, y: 100 + offset },
        data: defaultData,
      };
      setNodes([...nodes, newNode]);
      setAddMenuOpen(false);
    },
    [nodes, setNodes],
  );

  /** 打开保存弹窗 */
  const handleSave = useCallback(() => {
    setDraftName(currentAgentName || '');
    setSaveDialogOpen(true);
  }, [currentAgentName]);

  /** 确认保存 */
  const handleConfirmSave = useCallback(async () => {
    if (!draftName.trim()) return;
    setSaveLoading(true);
    try {
      setCurrentAgent(currentAgentId || null, draftName);
      const payload = exportToAgentDefinition();
      const saved = await saveOrUpdateAgent(currentAgentId || null, {
        ...payload,
        name: draftName,
      });
      setCurrentAgent(saved.agentId, saved.name);
      setSaveDialogOpen(false);
    } catch (err) {
      console.error('保存失败', err);
      alert('保存失败，请检查控制台');
    } finally {
      setSaveLoading(false);
    }
  }, [
    draftName,
    currentAgentId,
    setCurrentAgent,
    exportToAgentDefinition,
  ]);

  /** 导出 YAML（占位） */
  const handleExportYaml = useCallback(() => {
    const payload = exportToAgentDefinition();
    const yaml = JSON.stringify(payload, null, 2);
    const blob = new Blob([yaml], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${currentAgentName || 'agent'}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }, [exportToAgentDefinition, currentAgentName]);

  const menuItems = [
    { type: 'start', icon: CircleDot, label: '开始节点' },
    { type: 'end', icon: CircleDot, label: '结束节点' },
    { type: 'agent', icon: Cpu, label: 'Agent 节点' },
  ];

  return (
    <>
      <div className="pointer-events-auto absolute left-1/2 top-4 z-10 flex -translate-x-1/2 items-center gap-2 rounded-lg border border-gray-200 bg-white/90 px-3 py-2 shadow-sm backdrop-blur-sm">
        {/* 添加节点 —— 仅专家模式可见 */}
        {mode === 'expert' && (
          <div className="relative">
            <Button
              variant="outline"
              size="sm"
              className="gap-1"
              onClick={() => setAddMenuOpen((prev) => !prev)}
            >
              <Plus className="size-4" />
              添加节点
              <ChevronDown className="size-3" />
            </Button>
            {addMenuOpen && (
              <div className="absolute left-0 top-full z-50 mt-1 min-w-[140px] rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                {menuItems.map((item) => (
                  <button
                    key={item.type}
                    className="flex w-full items-center gap-2 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
                    onClick={() => addNode(item.type)}
                  >
                    <item.icon className="size-4 text-gray-400" />
                    {item.label}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {/* 保存 */}
        <Button variant="outline" size="sm" className="gap-1" onClick={handleSave}>
          <Save className="size-4" />
          保存
        </Button>

        {/* 导出 YAML —— 仅专家模式可见 */}
        {mode === 'expert' && (
          <Button
            variant="outline"
            size="sm"
            className="gap-1"
            onClick={handleExportYaml}
          >
            <FileDown className="size-4" />
            导出 YAML
          </Button>
        )}
      </div>

      {/* 保存弹窗 */}
      {saveDialogOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="w-[400px] rounded-lg border border-gray-200 bg-white p-5 shadow-lg">
            <h3 className="mb-4 text-base font-semibold text-gray-900">
              {currentAgentId ? '更新 Agent' : '创建新 Agent'}
            </h3>
            <div className="mb-3 flex flex-col gap-1">
              <label className="text-xs text-gray-500">Agent 名称 *</label>
              <Input
                value={draftName}
                onChange={(e) => setDraftName(e.target.value)}
                placeholder="输入 Agent 名称"
              />
            </div>
            <div className="flex justify-end gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setSaveDialogOpen(false)}
              >
                取消
              </Button>
              <Button
                size="sm"
                disabled={!draftName.trim() || saveLoading}
                onClick={handleConfirmSave}
              >
                {saveLoading && <Loader2 className="mr-1 size-3 animate-spin" />}
                确认保存
              </Button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

export default memo(CanvasToolbar);
