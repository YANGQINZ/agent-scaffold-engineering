/**
 * SSE 流式对话 Hook
 * 封装 streamChat API，提供 startStream / stopStream / isStreaming
 * 组件卸载时自动清理
 */
import { useRef, useCallback, useState } from 'react';
import { streamChat, type ChatRequest, type StreamEvent } from '@/api/chat';
import { useChatStore } from '@/stores/chat';
import { useCanvasStore } from '@/stores/canvas';

interface UseSSEReturn {
  /** 发起流式对话 */
  startStream: (params: ChatRequest) => void;
  /** 终止当前流式对话 */
  stopStream: () => void;
  /** 是否正在流式输出 */
  isStreaming: boolean;
}

/**
 * SSE 流式对话 Hook
 *
 * @param onEvent 自定义事件回调（可选），在 store 更新之后调用
 */
export function useSSE(onEvent?: (event: StreamEvent) => void): UseSSEReturn {
  const [isStreaming, setIsStreaming] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  // 从 chatStore 获取 actions
  const appendToLastMessage = useChatStore((s) => s.appendToLastMessage);
  const setThinkingContent = useChatStore((s) => s.setThinkingContent);
  const appendToThinkingContent = useChatStore((s) => s.appendToThinkingContent);
  const setActiveSessionId = useChatStore((s) => s.setActiveSessionId);
  const addNodeStatus = useChatStore((s) => s.addNodeStatus);
  const updateNodeStatus = useChatStore((s) => s.updateNodeStatus);
  const clearNodeStatuses = useChatStore((s) => s.clearNodeStatuses);

  /** 终止流式输出 */
  const stopStream = useCallback(() => {
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    setIsStreaming(false);
  }, []);

  /** 发起流式对话 */
  const startStream = useCallback(
    (params: ChatRequest) => {
      // 如果已有流在运行，先终止
      stopStream();
      clearNodeStatuses();
      setIsStreaming(true);

      // 处理 SSE 事件
      const controller = streamChat(params, (event: StreamEvent) => {
        // 如果收到错误事件，显示统一提示并结束
        if (event.type === 'ERROR') {
          const detail = (event.data?.error as string) || (event.data?.errorMessage as string) || '';
          console.warn('[SSE] 请求错误:', detail);
          appendToLastMessage('对话请求失败，请检查配置后重试');
          setIsStreaming(false);
          abortRef.current = null;
          return;
        }
        if (event.data && (event.data as Record<string, unknown>).errorCode) {
          const detail = ((event.data as Record<string, unknown>).errorMessage as string) || '';
          console.warn('[SSE] 业务错误:', detail);
          appendToLastMessage('对话请求失败，请检查配置后重试');
          setIsStreaming(false);
          abortRef.current = null;
          return;
        }
        switch (event.type) {
          case 'TEXT_DELTA': {
            // data 可能是字符串或 { text: string }
            const text =
              typeof event.data === 'string'
                ? event.data
                : (event.data?.text as string) ?? '';
            appendToLastMessage(text);
            break;
          }
          case 'THINKING': {
            const text =
              typeof event.data === 'string'
                ? event.data
                : (event.data?.thought as string) ?? (event.data?.text as string) ?? '';
            // 流式逐 token 追加思考内容
            appendToThinkingContent(text);
            break;
          }
          case 'NODE_START': {
            const nodeId =
              typeof event.data === 'string'
                ? event.data
                : (event.data?.nodeName as string) ?? (event.data?.nodeId as string) ?? '';
            if (nodeId) {
              addNodeStatus({ nodeId, status: 'running' });
              useCanvasStore.getState().setNodeState(nodeId, { status: 'running' });
            }
            break;
          }
          case 'NODE_END': {
            const nodeId =
              typeof event.data === 'string'
                ? event.data
                : (event.data?.nodeName as string) ?? (event.data?.nodeId as string) ?? '';
            if (nodeId) {
              updateNodeStatus(nodeId, 'done');
              useCanvasStore.getState().setNodeState(nodeId, { status: 'done' });
            }
            break;
          }
          case 'RAG_RETRIEVE': {
            // 处理 RAG 检索来源
            const sources = event.data?.sources as Array<{
              docName: string;
              chunkContent: string;
              score: number;
            }> | undefined;
            if (sources && sources.length > 0) {
              useChatStore.getState().setSources(sources);
            }
            break;
          }
          case 'DONE': {
            // 检查 DONE 事件中是否携带错误信息
            const errorMsg = event.data?.error as string | undefined;
            if (errorMsg) {
              console.warn('[SSE] 流式响应异常结束:', errorMsg);
            }
            // 清除画布节点运行状态
            useCanvasStore.getState().clearNodeStates();
            if (event.sessionId) {
              setActiveSessionId(event.sessionId);
            }
            setIsStreaming(false);
            abortRef.current = null;
            break;
          }
          default:
            break;
        }

        // 调用自定义回调
        onEvent?.(event);
      });

      abortRef.current = controller;
    },
    [
      stopStream,
      clearNodeStatuses,
      appendToLastMessage,
      setThinkingContent,
      appendToThinkingContent,
      setActiveSessionId,
      addNodeStatus,
      updateNodeStatus,
      onEvent,
    ],
  );

  return { startStream, stopStream, isStreaming };
}
