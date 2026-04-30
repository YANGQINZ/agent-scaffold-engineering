/**
 * 应用根组件
 * BrowserRouter + i18n + 懒加载路由
 */
import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import '@/i18n';
import Layout from '@/components/Layout';

/* 懒加载页面组件 */
const WorkspacePage = lazy(() => import('@/pages/WorkspacePage'));
const ChatPage = lazy(() => import('@/pages/ChatPage'));
const SimpleChatPage = lazy(() => import('@/pages/SimpleChatPage'));
const KnowledgePage = lazy(() => import('@/pages/KnowledgePage'));

/** 页面加载占位 */
function PageLoading() {
  return (
    <div className="flex h-full items-center justify-center text-muted-foreground">
      加载中...
    </div>
  );
}

function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<PageLoading />}>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<WorkspacePage />} />
            <Route path="/chat" element={<ChatPage />} />
            <Route path="/simple" element={<SimpleChatPage />} />
            <Route path="/knowledge" element={<KnowledgePage />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;
