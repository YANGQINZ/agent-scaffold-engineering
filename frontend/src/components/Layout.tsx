/**
 * 全局布局组件
 * 顶部导航栏：Logo + 3个标签页 + 简单/专家切换 + 中/英语言切换
 */
import { useLocation, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Outlet } from 'react-router-dom';
import { cn } from '@/lib/utils';

/** 导航标签页配置 */
const NAV_TABS = [
  { path: '/', labelKey: 'nav.workspace' },
  { path: '/chat', labelKey: 'nav.chat' },
  { path: '/knowledge', labelKey: 'nav.knowledge' },
] as const;

function Layout() {
  const { t, i18n } = useTranslation();
  const location = useLocation();

  /** 当前语言是否为中文 */
  const isZh = i18n.language === 'zh';

  /** 切换语言 */
  const toggleLanguage = () => {
    i18n.changeLanguage(isZh ? 'en' : 'zh');
  };

  /** 判断标签是否激活 */
  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/';
    return location.pathname.startsWith(path);
  };

  return (
    <div className="flex h-screen flex-col">
      {/* 顶部导航栏 */}
      <header className="flex h-14 shrink-0 items-center border-b border-border bg-background px-4">
        {/* Logo */}
        <Link to="/" className="mr-6 flex items-center gap-2 font-semibold tracking-tight">
          <span className="text-lg text-primary">Agent Studio</span>
        </Link>

        {/* 导航标签页 */}
        <nav className="flex items-center gap-1">
          {NAV_TABS.map((tab) => (
            <Link
              key={tab.path}
              to={tab.path}
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
              className={cn(
                'rounded-md px-2.5 py-1 text-xs font-medium transition-colors',
                true
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground',
              )}
              title={t('mode.simple')}
            >
              {t('mode.simple')}
            </button>
            <button
              type="button"
              className={cn(
                'rounded-md px-2.5 py-1 text-xs font-medium transition-colors',
                false
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
