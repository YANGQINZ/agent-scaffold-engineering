/**
 * 创建知识库对话框
 * 表单：名称输入 + 文件选择
 * 提交后调用 createKnowledgeBase API 创建知识库并上传首个文档
 */
import { memo, useCallback, useRef, useState } from 'react';
import { Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { createKnowledgeBase } from '@/api/knowledge';

interface CreateBaseDialogProps {
  /** 创建成功回调 */
  onCreated: () => void;
}

function CreateBaseDialog({ onCreated }: CreateBaseDialogProps) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  /** 提交创建 */
  const handleSubmit = useCallback(async () => {
    if (!name.trim() || !file) return;
    setLoading(true);
    try {
      await createKnowledgeBase(name.trim(), file);
      setOpen(false);
      setName('');
      setFile(null);
      onCreated();
    } catch (err) {
      console.error('创建知识库失败:', err);
    } finally {
      setLoading(false);
    }
  }, [name, file, onCreated]);

  /** 选择文件 */
  const handleFileChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const selected = e.target.files?.[0] ?? null;
      setFile(selected);
    },
    [],
  );

  /** 打开文件选择器 */
  const handleOpenFilePicker = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  /** 重置表单 */
  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      setOpen(nextOpen);
      if (!nextOpen) {
        setName('');
        setFile(null);
      }
    },
    [],
  );

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger
        render={<Button size="default">+ 创建知识库</Button>}
      />
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>创建知识库</DialogTitle>
          <DialogDescription>
            输入知识库名称并选择一个文件作为初始文档
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <div className="space-y-1.5">
            <label className="text-sm font-medium text-gray-700">
              名称 <span className="text-red-500">*</span>
            </label>
            <Input
              placeholder="请输入知识库名称"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-sm font-medium text-gray-700">
              文档文件 <span className="text-red-500">*</span>
            </label>
            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              accept=".pdf,.docx,.doc,.txt,.md"
              onChange={handleFileChange}
            />
            <Button
              variant="outline"
              className="w-full justify-start text-gray-500"
              onClick={handleOpenFilePicker}
            >
              <Upload className="size-4" />
              {file ? file.name : '选择文件（PDF、Word、TXT、MD）'}
            </Button>
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => handleOpenChange(false)}
            disabled={loading}
          >
            取消
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={!name.trim() || !file || loading}
          >
            {loading ? '创建中...' : '创建'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default memo(CreateBaseDialog);
