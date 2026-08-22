import { ArrowRight, BookOpen, ClipboardList, TicketCheck } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useT } from '@/i18n';

const LINKS = [
  {
    to: '/portal/catalog',
    titleKey: 'nav.catalog',
    hintKey: 'portal.catalogHint',
    icon: ClipboardList,
  },
  {
    to: '/portal/knowledge',
    titleKey: 'nav.knowledge',
    hintKey: 'portal.knowledgeHint',
    icon: BookOpen,
  },
  {
    to: '/portal/requests',
    titleKey: 'nav.myRequests',
    hintKey: 'portal.requestsHint',
    icon: TicketCheck,
  },
] as const;

export function PortalHomePage() {
  const t = useT();
  return (
    <section className="page page--portal-home">
      <div className="page-head">
        <div>
          <h1>{t('portal.homeTitle')}</h1>
          <p className="page-subtitle">{t('portal.homeSubtitle')}</p>
        </div>
      </div>
      <nav className="service-grid" aria-label={t('app.primaryNav')}>
        {LINKS.map(({ to, titleKey, hintKey, icon: Icon }) => (
          <Link key={to} to={to} className="service-card">
            <span>
              <Icon size={17} aria-hidden />
            </span>
            <b>{t(titleKey)}</b>
            <p>{t(hintKey)}</p>
            <ArrowRight className="service-arrow" size={17} aria-hidden />
          </Link>
        ))}
      </nav>
    </section>
  );
}
