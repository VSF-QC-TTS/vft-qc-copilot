import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { Application } from '@/app/application';
import { appRouter } from '@/app/router/router';
import '@/shared/styles/globals.css';

const container = document.getElementById('root');

if (!container) {
  throw new Error('Root element not found');
}

createRoot(container).render(
  <StrictMode>
    <Application router={appRouter} />
  </StrictMode>,
);
