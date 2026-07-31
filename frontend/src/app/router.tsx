import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { OverviewPage } from '@/pages/Overview/OverviewPage';
import { MyWorkPage } from '@/pages/MyWork/MyWorkPage';
import { QueuesPage } from '@/pages/Queues/QueuesPage';
import { CatalogPage } from '@/pages/Catalog/CatalogPage';
import { KnowledgePage } from '@/pages/Knowledge/KnowledgePage';
import { CmdbPage } from '@/pages/CMDB/CmdbPage';
import { AssetsPage } from '@/pages/Assets/AssetsPage';
import { ProblemsPage } from '@/pages/Problems/ProblemsPage';
import { ChangesPage } from '@/pages/Changes/ChangesPage';
import { SettingsPage } from '@/pages/Settings/SettingsPage';
import { ReportsPage } from '@/pages/Reports/ReportsPage';
import { MetadataPage } from '@/pages/Admin/MetadataPage';
import { WorkItemDetailPage } from '@/pages/WorkItemDetail/WorkItemDetailPage';
import { AuthCallbackPage } from '@/pages/Auth/CallbackPage';

export const router = createBrowserRouter([
  {
    path: '/auth/callback',
    element: <AuthCallbackPage />,
  },
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <OverviewPage /> },
      { path: 'my-work', element: <MyWorkPage /> },
      { path: 'queues', element: <QueuesPage /> },
      { path: 'catalog', element: <CatalogPage /> },
      { path: 'knowledge', element: <KnowledgePage /> },
      { path: 'cmdb', element: <CmdbPage /> },
      { path: 'assets', element: <AssetsPage /> },
      { path: 'problems', element: <ProblemsPage /> },
      { path: 'changes', element: <ChangesPage /> },
      { path: 'reports', element: <ReportsPage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: 'admin/metadata', element: <MetadataPage /> },
      { path: 'work-items/:id', element: <WorkItemDetailPage /> },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]);
