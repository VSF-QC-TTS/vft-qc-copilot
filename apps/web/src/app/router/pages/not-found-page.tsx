import { Link } from 'react-router';

export function NotFoundPage() {
  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-6 text-foreground">
      <section className="w-full max-w-xl border border-border bg-surface p-8">
        <p className="mb-2 text-sm text-foreground-muted">404</p>
        <h1 className="text-2xl font-semibold tracking-tight">Không tìm thấy trang</h1>
        <p className="mt-3 text-sm text-foreground-secondary">
          Đường dẫn này không tồn tại hoặc đã được thay đổi.
        </p>
        <Link
          className="mt-6 inline-flex rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus-ring active:bg-primary-active"
          to="/"
        >
          Về trang chính
        </Link>
      </section>
    </main>
  );
}
