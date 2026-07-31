import { lazy, Suspense, type ReactNode } from 'react';
import {
  createBrowserRouter,
  Navigate,
  useParams,
} from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { PageLoader } from '@/components/ui/PageLoader';
import { AuthCallbackPage } from '@/pages/Auth/CallbackPage';

/** Redirect `/module/:id` → `/module?param=id` (S7 path-route remainder). */
function ModuleIdRedirect({
  base,
  param,
}: {
  base: string;
  param: string;
}) {
  const { id } = useParams();
  const qs = id ? `?${param}=${encodeURIComponent(id)}` : '';
  return <Navigate to={`${base}${qs}`} replace />;
}

/* Eager: auth callback + shell. Pages are code-split. */
const OverviewPage = lazy(() =>
  import('@/pages/Overview/OverviewPage').then((m) => ({ default: m.OverviewPage })),
);
const MyWorkPage = lazy(() =>
  import('@/pages/MyWork/MyWorkPage').then((m) => ({ default: m.MyWorkPage })),
);
const QueuesPage = lazy(() =>
  import('@/pages/Queues/QueuesPage').then((m) => ({ default: m.QueuesPage })),
);
const CatalogPage = lazy(() =>
  import('@/pages/Catalog/CatalogPage').then((m) => ({ default: m.CatalogPage })),
);
const KnowledgePage = lazy(() =>
  import('@/pages/Knowledge/KnowledgePage').then((m) => ({ default: m.KnowledgePage })),
);
const CmdbPage = lazy(() =>
  import('@/pages/CMDB/CmdbPage').then((m) => ({ default: m.CmdbPage })),
);
const AssetsPage = lazy(() =>
  import('@/pages/Assets/AssetsPage').then((m) => ({ default: m.AssetsPage })),
);
const ProblemsPage = lazy(() =>
  import('@/pages/Problems/ProblemsPage').then((m) => ({ default: m.ProblemsPage })),
);
const ChangesPage = lazy(() =>
  import('@/pages/Changes/ChangesPage').then((m) => ({ default: m.ChangesPage })),
);
const SettingsPage = lazy(() =>
  import('@/pages/Settings/SettingsPage').then((m) => ({ default: m.SettingsPage })),
);
const ReportsPage = lazy(() =>
  import('@/pages/Reports/ReportsPage').then((m) => ({ default: m.ReportsPage })),
);
const MetadataPage = lazy(() =>
  import('@/pages/Admin/MetadataPage').then((m) => ({ default: m.MetadataPage })),
);
const AutomationPage = lazy(() =>
  import('@/pages/Admin/AutomationPage').then((m) => ({ default: m.AutomationPage })),
);
const WorkflowPage = lazy(() =>
  import('@/pages/Admin/WorkflowPage').then((m) => ({ default: m.WorkflowPage })),
);
const SlaPage = lazy(() =>
  import('@/pages/Admin/SlaPage').then((m) => ({ default: m.SlaPage })),
);
const AuditPage = lazy(() =>
  import('@/pages/Admin/AuditPage').then((m) => ({ default: m.AuditPage })),
);
const RbacPage = lazy(() =>
  import('@/pages/Admin/RbacPage').then((m) => ({ default: m.RbacPage })),
);
const SearchPage = lazy(() =>
  import('@/pages/Search/SearchPage').then((m) => ({ default: m.SearchPage })),
);
const WorkItemDetailPage = lazy(() =>
  import('@/pages/WorkItemDetail/WorkItemDetailPage').then((m) => ({
    default: m.WorkItemDetailPage,
  })),
);
const NotificationsPage = lazy(() =>
  import('@/pages/Notifications/NotificationsPage').then((m) => ({
    default: m.NotificationsPage,
  })),
);

function LazyRoute({ children }: { children: ReactNode }) {
  return <Suspense fallback={<PageLoader />}>{children}</Suspense>;
}

export const router = createBrowserRouter([
  {
    path: '/auth/callback',
    element: <AuthCallbackPage />,
  },
  {
    path: '/',
    element: <AppShell />,
    children: [
      {
        index: true,
        element: (
          <LazyRoute>
            <OverviewPage />
          </LazyRoute>
        ),
      },
      {
        path: 'my-work',
        element: (
          <LazyRoute>
            <MyWorkPage />
          </LazyRoute>
        ),
      },
      {
        path: 'queues',
        element: (
          <LazyRoute>
            <QueuesPage />
          </LazyRoute>
        ),
      },
      {
        path: 'catalog',
        element: (
          <LazyRoute>
            <CatalogPage />
          </LazyRoute>
        ),
      },
      {
        path: 'knowledge',
        element: (
          <LazyRoute>
            <KnowledgePage />
          </LazyRoute>
        ),
      },
      {
        path: 'cmdb',
        element: (
          <LazyRoute>
            <CmdbPage />
          </LazyRoute>
        ),
      },
      {
        path: 'assets',
        element: (
          <LazyRoute>
            <AssetsPage />
          </LazyRoute>
        ),
      },
      {
        path: 'problems',
        element: (
          <LazyRoute>
            <ProblemsPage />
          </LazyRoute>
        ),
      },
      {
        path: 'changes',
        element: (
          <LazyRoute>
            <ChangesPage />
          </LazyRoute>
        ),
      },
      {
        path: 'reports',
        element: (
          <LazyRoute>
            <ReportsPage />
          </LazyRoute>
        ),
      },
      {
        path: 'settings',
        element: (
          <LazyRoute>
            <SettingsPage />
          </LazyRoute>
        ),
      },
      {
        path: 'admin/metadata',
        element: (
          <LazyRoute>
            <MetadataPage />
          </LazyRoute>
        ),
      },
      {
        path: 'admin/automation',
        element: (
          <LazyRoute>
            <AutomationPage />
          </LazyRoute>
        ),
      },
      {
        path: 'admin/workflow',
        element: (
          <LazyRoute>
            <WorkflowPage />
          </LazyRoute>
        ),
      },
      {
        path: 'admin/sla',
        element: (
          <LazyRoute>
            <SlaPage />
          </LazyRoute>
        ),
      },
      {
        path: 'admin/audit',
        element: (
          <LazyRoute>
            <AuditPage />
          </LazyRoute>
        ),
      },
      {
        path: 'admin/rbac',
        element: (
          <LazyRoute>
            <RbacPage />
          </LazyRoute>
        ),
      },
      {
        path: 'search',
        element: (
          <LazyRoute>
            <SearchPage />
          </LazyRoute>
        ),
      },
      {
        path: 'work-items/:id',
        element: (
          <LazyRoute>
            <WorkItemDetailPage />
          </LazyRoute>
        ),
      },
      {
        path: 'notifications',
        element: (
          <LazyRoute>
            <NotificationsPage />
          </LazyRoute>
        ),
      },
      /* S7: stable path routes → module query deep-links (shareable URLs) */
      {
        path: 'problems/:id',
        element: <ModuleIdRedirect base="/problems" param="id" />,
      },
      {
        path: 'changes/:id',
        element: <ModuleIdRedirect base="/changes" param="id" />,
      },
      {
        path: 'assets/:id',
        element: <ModuleIdRedirect base="/assets" param="id" />,
      },
      {
        path: 'knowledge/:id',
        element: <ModuleIdRedirect base="/knowledge" param="article" />,
      },
      {
        path: 'cmdb/:id',
        element: <ModuleIdRedirect base="/cmdb" param="ci" />,
      },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]);
