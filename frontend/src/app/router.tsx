import { lazy, Suspense, type ReactNode } from 'react';
import {
  createBrowserRouter,
  Navigate,
  Outlet,
  useParams,
} from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { AdminShell } from '@/components/layout/AdminShell';
import { PortalShell } from '@/components/layout/PortalShell';
import { PageLoader } from '@/components/ui/PageLoader';
import { RouteErrorFallback } from '@/components/ui/ErrorBoundary';
import { AuthCallbackPage } from '@/pages/Auth/CallbackPage';

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
const ReleasesPage = lazy(() =>
  import('@/pages/Releases/ReleasesPage').then((m) => ({ default: m.ReleasesPage })),
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
const IdentityPage = lazy(() =>
  import('@/pages/Admin/IdentityPage').then((m) => ({ default: m.IdentityPage })),
);
const OnCallPage = lazy(() =>
  import('@/pages/Admin/OnCallPage').then((m) => ({ default: m.OnCallPage })),
);
const AnnouncementsPage = lazy(() =>
  import('@/pages/Admin/AnnouncementsPage').then((m) => ({ default: m.AnnouncementsPage })),
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
const PortalHomePage = lazy(() =>
  import('@/pages/Portal/PortalHomePage').then((m) => ({
    default: m.PortalHomePage,
  })),
);

function LazyRoute({ children }: { children: ReactNode }) {
  return <Suspense fallback={<PageLoader />}>{children}</Suspense>;
}

export const router = createBrowserRouter([
  {
    element: <Outlet />,
    errorElement: <RouteErrorFallback />,
    children: [
  {
    path: '/auth/callback',
    element: <AuthCallbackPage />,
  },
  {
    path: '/admin',
    element: <AdminShell />,
    children: [
      {
        index: true,
        element: <Navigate to="metadata" replace />,
      },
      {
        path: 'metadata',
        element: (
          <LazyRoute>
            <MetadataPage />
          </LazyRoute>
        ),
      },
      {
        path: 'automation',
        element: (
          <LazyRoute>
            <AutomationPage />
          </LazyRoute>
        ),
      },
      {
        path: 'workflow',
        element: (
          <LazyRoute>
            <WorkflowPage />
          </LazyRoute>
        ),
      },
      {
        path: 'sla',
        element: (
          <LazyRoute>
            <SlaPage />
          </LazyRoute>
        ),
      },
      {
        path: 'audit',
        element: (
          <LazyRoute>
            <AuditPage />
          </LazyRoute>
        ),
      },
      {
        path: 'rbac',
        element: (
          <LazyRoute>
            <RbacPage />
          </LazyRoute>
        ),
      },
      {
        path: 'identity',
        element: (
          <LazyRoute>
            <IdentityPage />
          </LazyRoute>
        ),
      },
      {
        path: 'oncall',
        element: (
          <LazyRoute>
            <OnCallPage />
          </LazyRoute>
        ),
      },
      {
        path: 'announcements',
        element: (
          <LazyRoute>
            <AnnouncementsPage />
          </LazyRoute>
        ),
      },
      { path: '*', element: <Navigate to="/admin/metadata" replace /> },
    ],
  },
  {
    path: '/portal',
    element: <PortalShell />,
    children: [
      {
        index: true,
        element: (
          <LazyRoute>
            <PortalHomePage />
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
        path: 'requests',
        element: (
          <LazyRoute>
            <MyWorkPage />
          </LazyRoute>
        ),
      },
      { path: '*', element: <Navigate to="/portal" replace /> },
    ],
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
        path: 'releases',
        element: (
          <LazyRoute>
            <ReleasesPage />
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
    ],
  },
]);
