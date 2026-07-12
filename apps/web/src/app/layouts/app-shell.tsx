import { useEffect, useState } from 'react';
import { AppHeader } from '@/app/layouts/app-header';
import { GlobalSidebar } from '@/app/layouts/global-sidebar';
import { Outlet } from 'react-router';

export function AppShell() {
  const [isMobileNavigationOpen, setIsMobileNavigationOpen] = useState(false);

  useEffect(() => {
    if (!isMobileNavigationOpen) {
      return undefined;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsMobileNavigationOpen(false);
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isMobileNavigationOpen]);

  return (
    <div className="flex min-h-screen w-full bg-background">
      <GlobalSidebar className="hidden lg:flex" />
      {isMobileNavigationOpen && (
        <dialog
          aria-label="Điều hướng ứng dụng"
          aria-modal="true"
          className="fixed inset-0 z-50 m-0 h-full max-h-none w-full max-w-none border-0 bg-transparent p-0 lg:hidden"
          open
        >
          <button
            aria-label="Đóng menu"
            className="absolute inset-0 bg-overlay"
            onClick={() => setIsMobileNavigationOpen(false)}
            type="button"
          />
          <GlobalSidebar
            className="relative h-full"
            id="mobile-navigation"
            onNavigate={() => setIsMobileNavigationOpen(false)}
          />
        </dialog>
      )}
      <div className="flex flex-1 flex-col">
        <AppHeader
          isMenuOpen={isMobileNavigationOpen}
          onMenuClick={() => setIsMobileNavigationOpen(true)}
        />
        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
