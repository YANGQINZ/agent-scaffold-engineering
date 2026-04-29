/**
 * SSE 流式对话 Hook
 * 封装 streamChat API，提供 startStream / stopStream / isStreaming
 * 组件卸载时自动清理
 */
import { useRef, useCallback, useState } from 'react';
import { streamChat, type ChatRequest, type StreamEvent } from '@/api/chat';
import { useChatStore } from '@/stores/chat';

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
                : (event.data?.text as string) ?? (event.data?.content as string) ?? '';
            setThinkingContent(text);
            break;
          }
          case 'NODE_START': {
            const nodeId =
              typeof event.data === 'string'
                ? event.data
                : (event.data?.nodeId as string) ?? '';
            if (nodeId) {
              addNodeStatus({ nodeId, status: 'running' });
            }
            break;
          }
          case 'NODE_END': {
            const nodeId =
              typeof event.data === 'string'
                ? event.data
                : (event.data?.nodeId as string) ?? '';
            if (nodeId) {
              updateNodeStatus(nodeId, 'done');
            }
            break;
          }
          case 'DONE': {
            if (event.sessionId) {
              setActiveSessionId(event.sessionId);
            }
            // 流结束
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
      setActiveSessionId,
      addNodeStatus,
      updateNodeStatus,
      onEvent,
    ],
  );

  return { startStream, stopStream, isStreaming };
}
