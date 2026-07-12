import type { ComponentProps } from 'react';
import { RouterProvider } from 'react-router/dom';

import { AppProviders } from '@/app/providers/app-providers';
import { NotificationToaster } from '@/shared/components/toast/notification-toaster';

interface ApplicationProps {
  router: ComponentProps<typeof RouterProvider>['router'];
}

export function Application({ router }: ApplicationProps) {
  return (
    <AppProviders>
      <RouterProvider router={router} />
      <NotificationToaster />
    </AppProviders>
  );
}
