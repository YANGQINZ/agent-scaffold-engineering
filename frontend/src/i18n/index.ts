/**
 * i18next 初始化配置
 * 默认中文，支持中/英切换
 */
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import zh from './zh';
import en from './en';

i18n.use(initReactI18next).init({
  resources: {
    zh: { translation: zh },
    en: { translation: en },
  },
  lng: 'zh', // 默认中文
  fallbackLng: 'zh',
  interpolation: {
    escapeValue: false, // React 已自带 XSS 防护
  },
});

export default i18n;
