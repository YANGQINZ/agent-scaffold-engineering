/**
 * 节点编辑面板（右侧边栏）
 * 选中画布节点后展示属性编辑表单，包含四个可折叠分区
 */
import { memo, useCallback, useEffect, useMemo, useState } from 'react';
import {
  ChevronDown,
  ChevronRight,
  Database,
  Pencil,
  Plus,
  Server,
  Trash2,
  GitBranch,
} from 'lucide-react';
import { useCanvasStore } from '@/stores/canvas';
import { useAppStore } from '@/stores/app';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import McpServerForm from './McpServerForm';
import type { McpServerConfig } from '@/api/agent';
import { listKnowledgeBases, type KnowledgeBase } from '@/api/knowledge';

// ═══════════════════════════════════════════════════════════
// 类型定义
// ═══════════════════════════════════════════════════════════

/** 可折叠分区 key */
type SectionKey = 'basic' | 'rag' | 'mcp' | 'edges';

/** 节点自定义数据结构 */
interface NodeEditData {
  label: string;
  instruction?: string;
  subEngine?: 'GRAPH' | 'AGENTSCOPE';
  ragEnabled?: boolean;
  knowledgeBaseId?: string;
  mcpServers?: McpServerConfig[];
}

// ═══════════════════════════════════════════════════════════
// 可折叠分区
// ═══════════════════════════════════════════════════════════

interface SectionProps {
  title: string;
  icon?: React.ReactNode;
  defaultOpen?: boolean;
  sectionKey?: SectionKey;
  children: React.ReactNode;
}

function Section({ title, icon, defaultOpen = false, children }: SectionProps) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div className="flex flex-col">
      <button
        className="flex w-full items-center gap-2 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
        onClick={() => setOpen((prev) => !prev)}
      >
        {open ? (
          <ChevronDown className="size-4 text-gray-400" />
        ) : (
          <ChevronRight className="size-4 text-gray-400" />
        )}
        {icon}
        {title}
      </button>
      {open && <div className="flex flex-col gap-3 px-3 pb-3">{children}</div>}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════
// 主组件
// ═══════════════════════════════════════════════════════════

function NodeEditPanel() {
  const mode = useAppStore((s) => s.mode);
  const { nodes, edges, selectedNodeId, setNodes, setEdges } = useCanvasStore();

  // ─── 选中节点数据 ───
  const selectedNode = useMemo(
    () => nodes.find((n) => n.id === selectedNodeId),
    [nodes, selectedNodeId],
  );

  const nodeData = (selectedNode?.data ?? {}) as NodeEditData;

  // ─── 本地表单状态 ───
  const [label, setLabel] = useState(nodeData.label ?? '');
  const [instruction, setInstruction] = useState(nodeData.instruction ?? '');
  const [subEngine, setSubEngine] = useState<'GRAPH' | 'AGENTSCOPE'>(
    nodeData.subEngine ?? 'GRAPH',
  );
  const [ragEnabled, setRagEnabled] = useState(nodeData.ragEnabled ?? false);
  const [knowledgeBaseId, setKnowledgeBaseId] = useState(
    nodeData.knowledgeBaseId ?? '',
  );
  const [mcpServers, setMcpServers] = useState<McpServerConfig[]>(
    nodeData.mcpServers ?? [],
  );

  // ─── 知识库列表（延迟加载） ───
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);

  useEffect(() => {
    if (mode === 'expert' && selectedNodeId) {
      listKnowledgeBases().then(setKnowledgeBases).catch(() => {});
    }
  }, [mode, selectedNodeId]);

  // ─── MCP Server 弹窗状态 ───
  const [mcpDialogOpen, setMcpDialogOpen] = useState(false);
  const [editingMcp, setEditingMcp] = useState<McpServerConfig | undefined>();

  // ─── 同步选中节点数据到本地 ───
  useEffect(() => {
    if (!selectedNode) return;
    const d = selectedNode.data as NodeEditData;
    setLabel(d.label ?? '');
    setInstruction(d.instruction || '');
    setSubEngine(d.subEngine || 'GRAPH');
    setRagEnabled(d.ragEnabled ?? false);
    setKnowledgeBaseId(d.knowledgeBaseId ?? '');
    setMcpServers(d.mcpServers ?? []);
  }, [selectedNode]);

  // ─── 更新节点 data ───
  const updateNodeData = useCallback(
    (patch: Partial<NodeEditData>) => {
      if (!selectedNodeId) return;
      setNodes(
        nodes.map((n) =>
          n.id === selectedNodeId
            ? { ...n, data: { ...n.data, ...patch } }
            : n,
        ),
      );
    },
    [selectedNodeId, nodes, setNodes],
  );

  // ─── RAG 切换 ───
  const handleRagToggle = useCallback(() => {
    const next = !ragEnabled;
    setRagEnabled(next);
    updateNodeData({ ragEnabled: next });
  }, [ragEnabled, updateNodeData]);

  // ─── 知识库选择 ───
  const handleKnowledgeBaseChange = useCallback(
    (value: string) => {
      setKnowledgeBaseId(value);
      updateNodeData({ knowledgeBaseId: value || undefined });
    },
    [updateNodeData],
  );

  // ─── MCP Server 操作 ───
  const handleMcpSubmit = useCallback(
    (config: McpServerConfig) => {
      let updated: McpServerConfig[];
      if (editingMcp) {
        // 编辑模式：替换已有项
        updated = mcpServers.map((s) =>
          s.name === editingMcp.name ? config : s,
        );
      } else {
        // 新建模式：追加
        updated = [...mcpServers, config];
      }
      setMcpServers(updated);
      updateNodeData({ mcpServers: updated });
      setEditingMcp(undefined);
    },
    [editingMcp, mcpServers, updateNodeData],
  );

  const handleMcpDelete = useCallback(
    (name: string) => {
      const updated = mcpServers.filter((s) => s.name !== name);
      setMcpServers(updated);
      updateNodeData({ mcpServers: updated });
    },
    [mcpServers, updateNodeData],
  );

  const handleMcpEdit = useCallback((config: McpServerConfig) => {
    setEditingMcp(config);
    setMcpDialogOpen(true);
  }, []);

  const handleMcpAdd = useCallback(() => {
    setEditingMcp(undefined);
    setMcpDialogOpen(true);
  }, []);

  // ─── 出边条件编辑 ───
  const outgoingEdges = useMemo(
    () => edges.filter((e) => e.source === selectedNodeId),
    [edges, selectedNodeId],
  );

  const handleConditionChange = useCallback(
    (edgeId: string, condition: string) => {
      setEdges(
        edges.map((e) =>
          e.id === edgeId ? { ...e, data: { ...e.data, condition } } : e,
        ),
      );
    },
    [edges, setEdges],
  );

  // ─── Start/End 节点无可配置项 ───
  if (selectedNode && (selectedNode.type === 'start' || selectedNode.type === 'end')) {
    return (
      <div className="w-80 border-r bg-white p-4 flex items-center justify-center text-slate-400 text-sm">
        该节点无可配置项
      </div>
    );
  }

  // ─── 无选中节点时不渲染 ───
  if (!selectedNodeId || !selectedNode) {
    return (
      <div className="flex h-full w-[320px] items-center justify-center border-r border-gray-200 bg-white shadow-lg">
        <p className="text-sm text-gray-400">点击节点以编辑属性</p>
      </div>
    );
  }

  const isExpert = mode === 'expert';

  return (
    <div className="flex h-full w-[320px] flex-col border-r border-gray-200 bg-white overflow-y-auto shadow-lg">
      {/* 面板标题 */}
      <div className="flex items-center gap-2 border-b border-gray-100 px-3 py-2.5">
        <span className="text-sm font-semibold text-gray-800">节点属性</span>
        <Badge variant="secondary" className="text-xs">
          {selectedNode.type ?? 'node'}
        </Badge>
      </div>

      {/* ─────── 1. 基础信息 ─────── */}
      <Section title="基础信息" defaultOpen sectionKey="basic">
        <div className="space-y-3">
          <div>
            <label className="text-xs text-slate-500">节点名称</label>
            <input
              className="w-full border rounded px-2 py-1 text-sm"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              onBlur={() => updateNodeData({ label })}
            />
          </div>
          <div>
            <label className="text-xs text-slate-500">
              提示词 <span className="text-red-400">*</span>
            </label>
            <textarea
              className="w-full border rounded px-2 py-1 text-sm min-h-[80px] resize-y"
              value={instruction}
              onChange={(e) => setInstruction(e.target.value)}
              onBlur={() => updateNodeData({ instruction })}
              placeholder="请输入提示词..."
            />
          </div>
          <div>
            <label className="text-xs text-slate-500">子引擎类型</label>
            <select
              className="w-full border rounded px-2 py-1 text-sm"
              value={subEngine}
              onChange={(e) => {
                const val = e.target.value as 'GRAPH' | 'AGENTSCOPE';
                setSubEngine(val);
                updateNodeData({ subEngine: val });
              }}
            >
              <option value="GRAPH">GRAPH（默认）</option>
              <option value="AGENTSCOPE">AGENTSCOPE</option>
            </select>
          </div>
        </div>
      </Section>

      <Separator />

      {/* ─────── 2. RAG 配置（专家模式） ─────── */}
      {isExpert && (
        <>
          <Section
            title="RAG 配置"
            icon={<Database className="size-4 text-amber-500" />}
          >
            {/* 启用 RAG 开关 */}
            <div className="flex items-center justify-between">
              <label className="text-xs text-gray-500">启用 RAG</label>
              <button
                onClick={handleRagToggle}
                className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full transition-colors ${
                  ragEnabled ? 'bg-indigo-500' : 'bg-gray-200'
                }`}
              >
                <span
                  className={`pointer-events-none inline-block size-4 translate-y-0.5 rounded-full bg-white shadow-sm transition-transform ${
                    ragEnabled ? 'translate-x-[18px]' : 'translate-x-0.5'
                  }`}
                />
              </button>
            </div>

            {/* 知识库选择 */}
            {ragEnabled && (
              <div className="flex flex-col gap-1">
                <label className="text-xs text-gray-500">知识库</label>
                <select
                  value={knowledgeBaseId}
                  onChange={(e) => handleKnowledgeBaseChange(e.target.value)}
                  className="h-8 w-full rounded-lg border border-input bg-transparent px-2.5 py-1 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  <option value="">-- 请选择知识库 --</option>
                  {knowledgeBases.map((kb) => (
                    <option key={kb.baseId} value={kb.baseId}>
                      {kb.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </Section>

          <Separator />

          {/* ─────── 3. MCP Server（专家模式） ─────── */}
          <Section
            title="MCP Server"
            icon={<Server className="size-4 text-green-500" />}
          >
            {mcpServers.length === 0 && (
              <p className="text-xs text-gray-400">暂无配置的 MCP Server</p>
            )}

            {mcpServers.map((server) => (
              <div
                key={server.name}
                className="flex items-center gap-2 rounded-md border border-gray-100 px-2 py-1.5"
              >
                <div className="flex min-w-0 flex-1 flex-col">
                  <span className="truncate text-sm font-medium text-gray-700">
                    {server.name}
                  </span>
                  <span className="text-xs text-gray-400">
                    {server.transport === 'stdio'
                      ? `stdio: ${server.command ?? ''}`
                      : `${server.transport}: ${server.url ?? ''}`}
                  </span>
                </div>
                <Button
                  variant="ghost"
                  size="icon-xs"
                  onClick={() => handleMcpEdit(server)}
                >
                  <Pencil className="size-3 text-gray-400" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon-xs"
                  onClick={() => handleMcpDelete(server.name)}
                >
                  <Trash2 className="size-3 text-red-400" />
                </Button>
              </div>
            ))}

            <Button
              variant="outline"
              size="sm"
              className="w-full gap-1"
              onClick={handleMcpAdd}
            >
              <Plus className="size-3.5" />
              添加 MCP Server
            </Button>
          </Section>

          <Separator />
        </>
      )}

      {/* ─────── 4. 出边条件（所有模式可见） ─────── */}
      <Section
        title="出边条件"
        icon={<GitBranch className="size-4 text-purple-500" />}
        defaultOpen
      >
        {outgoingEdges.length === 0 && (
          <p className="text-xs text-gray-400">该节点无出边</p>
        )}

        {outgoingEdges.map((edge) => {
          const condition =
            (edge.data as Record<string, unknown> | undefined)?.condition as
              | string
              | undefined ?? '';

          return (
            <div key={edge.id} className="flex flex-col gap-1">
              <div className="flex items-center gap-1 text-xs text-gray-400">
                <GitBranch className="size-3" />
                <span>
                  {edge.source} → {edge.target}
                </span>
              </div>
              <Input
                value={condition}
                onChange={(e) =>
                  handleConditionChange(edge.id, e.target.value)
                }
                placeholder="条件表达式（可选）"
              />
            </div>
          );
        })}
      </Section>

      {/* MCP Server 编辑弹窗 */}
      <McpServerForm
        open={mcpDialogOpen}
        onOpenChange={setMcpDialogOpen}
        initialData={editingMcp}
        onSubmit={handleMcpSubmit}
      />
    </div>
  );
}

export default memo(NodeEditPanel);
