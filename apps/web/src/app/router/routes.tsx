import type { RouteObject } from 'react-router';

import { HomePage } from '@/app/router/pages/home-page';
import { NotFoundPage } from '@/app/router/pages/not-found-page';

export const appRoutes: RouteObject[] = [
  {
    path: '/',
    element: <HomePage />,
  },
  {
    path: '*',
    element: <NotFoundPage />,
  },
];
