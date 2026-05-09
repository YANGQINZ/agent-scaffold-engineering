/**
 * 全局布局组件
 * 顶部导航栏：Logo + 3个标签页 + 简单/专家切换 + 中/英语言切换
 */
import { useLocation, Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Outlet } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { useAppStore } from '@/stores/app';
import { useChatStore } from '@/stores/chat';

/** 导航标签页配置 */
const NAV_TABS = [
  { path: '/', labelKey: 'nav.workspace' },
  { path: '/chat', labelKey: 'nav.chat' },
  { path: '/mcp', labelKey: 'nav.mcp' },
  { path: '/knowledge', labelKey: 'nav.knowledge' },
] as const;

function Layout() {
  const { t, i18n } = useTranslation();
  const location = useLocation();
  const mode = useAppStore((s) => s.mode);
  const setMode = useAppStore((s) => s.setMode);
  const clearMessages = useChatStore((s) => s.clearMessages);
  const navigate = useNavigate();

  /** 当前语言是否为中文 */
  const isZh = i18n.language === 'zh';

  /** 切换语言 */
  const toggleLanguage = () => {
    i18n.changeLanguage(isZh ? 'en' : 'zh');
  };

  /** 简单模式下的实际导航路径映射 */
  const resolveNavPath = (path: string) => {
    if (path === '/chat' && mode === 'simple') return '/simple';
    return path;
  };

  /** 判断标签是否激活 */
  const isActive = (path: string) => {
    const resolved = resolveNavPath(path);
    if (resolved === '/') return location.pathname === '/';
    return location.pathname === resolved;
  };

  /** 点击导航标签 */
  const handleNavClick = (e: React.MouseEvent, path: string) => {
    // 简单模式下点击工作台 → 自动切换回专家模式
    if (path === '/' && mode === 'simple') {
      e.preventDefault();
      setMode('expert');
      navigate('/');
    }
  };

  return (
    <div className="flex h-screen flex-col">
      {/* 顶部导航栏 */}
      <header className="flex h-14 shrink-0 items-center border-b border-border bg-background px-4">
        {/* Logo */}
        <Link to="/" onClick={(e) => handleNavClick(e, '/')} className="mr-6 flex items-center gap-2 font-semibold tracking-tight">
          <span className="text-lg text-primary">Agent Studio</span>
        </Link>

        {/* 导航标签页 */}
        <nav className="flex items-center gap-1">
          {NAV_TABS.map((tab) => (
            <Link
              key={tab.path}
              to={resolveNavPath(tab.path)}
              onClick={(e) => handleNavClick(e, tab.path)}
              className={cn(
                'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                isActive(tab.path)
                  ? 'bg-primary/10 text-primary'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground',
              )}
            >
              {t(tab.labelKey)}
            </Link>
          ))}
        </nav>

        {/* 右侧操作区 */}
        <div className="ml-auto flex items-center gap-2">
          {/* 简单/专家模式切换 */}
          <div className="flex items-center gap-0.5 rounded-lg border border-border bg-muted/50 p-0.5">
            <button
              type="button"
              onClick={() => { setMode('simple'); clearMessages(); navigate('/simple'); }}
              className={cn(
                'rounded-md px-2.5 py-1 text-xs font-medium transition-colors',
                mode === 'simple'
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground',
              )}
              title={t('mode.simple')}
            >
              {t('mode.simple')}
            </button>
            <button
              type="button"
              onClick={() => {
                setMode('expert');
                clearMessages();
                // 在对话页切换时留在对话页，其他页面回到工作台
                navigate(location.pathname === '/simple' ? '/chat' : '/');
              }}
              className={cn(
                'rounded-md px-2.5 py-1 text-xs font-medium transition-colors',
                mode === 'expert'
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground',
              )}
              title={t('mode.expert')}
            >
              {t('mode.expert')}
            </button>
          </div>

          {/* 语言切换 */}
          <button
            type="button"
            onClick={toggleLanguage}
            className="rounded-md px-2.5 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            {isZh ? 'EN' : '中'}
          </button>
        </div>
      </header>

      {/* 页面内容区域 */}
      <main className="flex-1 overflow-hidden">
        <Outlet />
      </main>
    </div>
  );
}

export default Layout;
