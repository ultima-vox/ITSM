import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ChevronDown, Menu, Search } from 'lucide-react';
import { useI18n, useT } from '@/i18n';
import type { LocaleCode } from '@/types';
import { NotificationMenu } from './NotificationMenu';
import { ProfileMenu } from './ProfileMenu';

export interface CrumbItem {
  label: string;
  to?: string;
}

interface HeaderProps {
  onMenu: () => void;
  crumbs: CrumbItem[];
  onOpenCommand: () => void;
}

export function Header({ onMenu, crumbs, onOpenCommand }: HeaderProps) {
  const t = useT();
  const { locale, setLocale, locales } = useI18n();
  const [langOpen, setLangOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const location = useLocation();

  useEffect(() => {
    setLangOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!langOpen) return;
    const onDoc = (e: MouseEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) setLangOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setLangOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [langOpen]);

  const localeLabel = (code: LocaleCode) => t(`locale.${code}`);
  const isMac =
    typeof navigator !== 'undefined' &&
    /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent);
  const shortcut = isMac
    ? t('header.searchShortcutMac')
    : t('header.searchShortcutWin');

  return (
    <header className="app-header">
      <button
        type="button"
        className="mobile-menu"
        aria-label={t('app.menu')}
        onClick={onMenu}
      >
        <Menu size={20} />
      </button>

      <nav className="crumb" aria-label={t('header.breadcrumb')}>
        <Link to="/" className="crumb__root">
          {t('header.workspace')}
        </Link>
        {crumbs.map((c, i) => (
          <span key={`${c.label}-${i}`} className="crumb__seg">
            <b aria-hidden>/</b>
            {c.to && i < crumbs.length - 1 ? (
              <Link to={c.to}>{c.label}</Link>
            ) : (
              <strong aria-current={i === crumbs.length - 1 ? 'page' : undefined}>
                {c.label}
              </strong>
            )}
          </span>
        ))}
      </nav>

      <div className="header-actions">
        <button
          type="button"
          className="search search--btn"
          onClick={onOpenCommand}
          aria-label={t('header.searchPlaceholder')}
        >
          <Search size={18} aria-hidden />
          <span className="search__placeholder">{t('header.searchPlaceholder')}</span>
          <kbd>{shortcut}</kbd>
        </button>

        <NotificationMenu />

        <div className="language" ref={menuRef}>
          <button
            type="button"
            aria-expanded={langOpen}
            aria-haspopup="listbox"
            aria-label={t('header.language')}
            onClick={() => setLangOpen((v) => !v)}
          >
            <span>{locale.toUpperCase()}</span>
            <ChevronDown size={14} aria-hidden />
          </button>
          {langOpen && (
            <div className="language-menu" role="listbox" aria-label={t('header.language')}>
              <p>{t('header.language')}</p>
              {locales.map((code) => (
                <button
                  key={code}
                  type="button"
                  role="option"
                  aria-selected={locale === code}
                  className={locale === code ? 'is-selected' : undefined}
                  onClick={() => {
                    setLocale(code);
                    setLangOpen(false);
                  }}
                >
                  {localeLabel(code)}
                  <span>{code.toUpperCase()}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        <ProfileMenu />
      </div>
    </header>
  );
}
