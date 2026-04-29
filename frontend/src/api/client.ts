/**
 * API 客户端基础配置
 * axios 实例 + SSE 流式请求辅助函数
 */
import axios from 'axios';

/**
 * 后端统一响应格式
 * 对应后端 com.ai.agent.types.model.Response<T>
 */
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  errorCode: string | null;
  errorMessage: string | null;
}

/**
 * axios 实例 — 基础路径 /api/v1，Vite 代理到后端 8091 端口
 */
export const apiClient = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * 响应拦截器 — 自动解包 ApiResponse<T>，失败时抛出错误
 */
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const msg =
      error.response?.data?.errorMessage || error.message || '请求失败';
    console.error('[API 错误]', msg);
    return Promise.reject(new Error(msg));
  },
);

/**
 * SSE 流式请求辅助函数
 * 使用 fetch + ReadableStream 解析 text/event-stream
 *
 * @param url 请求路径（相对于 baseURL）
 * @param body 请求体
 * @param onEvent 每个解析出的事件回调
 * @returns AbortController 用于取消流
 */
export function fetchSSE<T = Record<string, unknown>>(
  url: string,
  body: Record<string, unknown>,
  onEvent: (event: T) => void,
): AbortController {
  const controller = new AbortController();

  fetch(`/api/v1${url}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`SSE 请求失败: ${response.status}`);
      }
      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('无法获取 ReadableStream');
      }

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // 按双换行符分割 SSE 事件
        const parts = buffer.split('\n\n');
        // 最后一段可能不完整，保留在 buffer 中
        buffer = parts.pop() || '';

        for (const part of parts) {
          const lines = part.split('\n');
          for (const line of lines) {
            // 解析 data: 行
            if (line.startsWith('data:')) {
              const jsonStr = line.slice(5).trim();
              if (!jsonStr) continue;
              try {
                const event = JSON.parse(jsonStr) as T;
                onEvent(event);
              } catch {
                console.warn('[SSE] 解析事件失败:', jsonStr);
              }
            }
          }
        }
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        console.error('[SSE] 流错误:', err);
      }
    });

  return controller;
}
