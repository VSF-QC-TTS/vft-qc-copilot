import type { ComponentProps } from 'react';
import { RouterProvider } from 'react-router/dom';

import { AppErrorBoundary } from '@/app/error/app-error-boundary';
import { AppProviders } from '@/app/providers/app-providers';
import { NotificationToaster } from '@/shared/components/toast/notification-toaster';

interface ApplicationProps {
  router: ComponentProps<typeof RouterProvider>['router'];
}

export function Application({ router }: ApplicationProps) {
  return (
    <AppProviders>
      <AppErrorBoundary>
        <RouterProvider router={router} />
      </AppErrorBoundary>
      <NotificationToaster />
    </AppProviders>
  );
}
