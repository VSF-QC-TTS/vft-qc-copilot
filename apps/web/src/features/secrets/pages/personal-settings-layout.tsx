import { Link, Outlet, useLocation } from 'react-router';

export function PersonalSettingsLayout() {
  const location = useLocation();

  const navItems = [
    { name: 'Secrets', href: `/settings/secrets` },
    { name: 'Providers', href: `/settings/providers` },
  ];

  return (
    <div className="flex flex-col space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight">Cài đặt cá nhân</h2>
        <p className="text-muted-foreground">Quản lý cấu hình và bảo mật cá nhân.</p>
      </div>

      <div className="flex flex-col space-y-8 lg:flex-row lg:space-x-12 lg:space-y-0">
        <aside className="lg:w-1/5">
          <nav className="flex space-x-2 lg:flex-col lg:space-x-0 lg:space-y-1">
            {navItems.map((item) => {
              const isActive = location.pathname.startsWith(item.href);
              return (
                <Link
                  key={item.href}
                  to={item.href}
                  className={`flex items-center rounded-md px-3 py-2 text-sm font-medium ${
                    isActive
                      ? 'bg-muted hover:bg-muted'
                      : 'text-muted-foreground hover:bg-transparent hover:underline'
                  }`}
                >
                  {item.name}
                </Link>
              );
            })}
          </nav>
        </aside>
        <div className="flex-1 lg:max-w-2xl">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
