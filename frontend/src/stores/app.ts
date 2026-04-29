/**
 * 全局应用状态
 * 管理模式切换、语言、活跃标签页
 */
import { create } from 'zustand';

/** 模式类型 */
export type AppMode = 'simple' | 'expert';

/** 语言类型 */
export type AppLanguage = 'zh' | 'en';

/** 活跃标签页 */
export type ActiveTab = 'workspace' | 'chat' | 'knowledge';

/** 应用全局状态 */
interface AppState {
  /** 当前模式（简单/专家） */
  mode: AppMode;
  /** 当前语言 */
  language: AppLanguage;
  /** 当前活跃标签页 */
  activeTab: ActiveTab;

  /** 设置模式 */
  setMode: (mode: AppMode) => void;
  /** 设置语言 */
  setLanguage: (lang: AppLanguage) => void;
  /** 设置活跃标签页 */
  setActiveTab: (tab: ActiveTab) => void;
}

export const useAppStore = create<AppState>((set) => ({
  mode: 'simple',
  language: 'zh',
  activeTab: 'workspace',

  setMode: (mode) => set({ mode }),
  setLanguage: (language) => set({ language }),
  setActiveTab: (activeTab) => set({ activeTab }),
}));
