/**
 * 画布顶部工具栏
 * 包含：添加节点（专家模式）、保存、测试运行、导出 YAML（专家模式）
 */
import { memo, useCallback, useState } from 'react';
import {
  Plus,
  Save,
  Play,
  FileDown,
  MessageSquare,
  Cpu,
  CircleDot,
  ChevronDown,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAppStore } from '@/stores/app';
import { useCanvasStore } from '@/stores/canvas';
import { cn } from '@/lib/utils';

function CanvasToolbar() {
  const mode = useAppStore((s) => s.mode);
  const { nodes, setNodes } = useCanvasStore();
  const [addMenuOpen, setAddMenuOpen] = useState(false);

  /** 生成唯一 ID */
  const genId = () =>
    `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

  /** 添加节点 */
  const addNode = useCallback(
    (type: string) => {
      const offset = nodes.length * 40;
      const newNode = {
        id: genId(),
        type,
        position: { x: 250 + offset, y: 100 + offset },
        data: {
          label:
            type === 'start'
              ? '开始'
              : type === 'end'
                ? '结束'
                : type === 'chat'
                  ? '对话节点'
                  : '引擎节点',
          ...(type === 'chat' ? { engine: 'CHAT' } : {}),
          ...(type === 'engine' ? { engineType: 'GRAPH' } : {}),
        },
      };
      setNodes([...nodes, newNode]);
      setAddMenuOpen(false);
    },
    [nodes, setNodes],
  );

  /** 保存（占位） */
  const handleSave = useCallback(() => {
    // TODO: 调用后端保存接口
  }, []);

  /** 测试运行（占位） */
  const handleTest = useCallback(() => {
    // TODO: 触发测试运行流程
  }, []);

  /** 导出 YAML（占位） */
  const handleExportYaml = useCallback(() => {
    // TODO: 导出 YAML
  }, []);

  const menuItems = [
    { type: 'start', icon: CircleDot, label: '开始节点' },
    { type: 'end', icon: CircleDot, label: '结束节点' },
    { type: 'chat', icon: MessageSquare, label: '对话节点' },
    { type: 'engine', icon: Cpu, label: '引擎节点' },
  ];

  return (
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

      {/* 测试运行 */}
      <Button variant="default" size="sm" className="gap-1" onClick={handleTest}>
        <Play className="size-4" />
        测试运行
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
  );
}

export default memo(CanvasToolbar);
