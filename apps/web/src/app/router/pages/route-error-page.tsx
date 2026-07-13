import { Link, isRouteErrorResponse, useRouteError } from 'react-router';

interface RouteErrorCopy {
  eyebrow: string;
  title: string;
  description: string;
}

export function routeErrorCopy(error: unknown): RouteErrorCopy {
  if (isRouteErrorResponse(error)) {
    if (error.status === 404) {
      return {
        eyebrow: '404',
        title: 'Không tìm thấy trang',
        description: 'Đường dẫn này không tồn tại hoặc đã được thay đổi.',
      };
    }

    if (error.status === 401) {
      return {
        eyebrow: '401',
        title: 'Cần đăng nhập lại',
        description: 'Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.',
      };
    }

    if (error.status === 403) {
      return {
        eyebrow: '403',
        title: 'Không có quyền truy cập',
        description: 'Tài khoản hiện tại không có quyền xem nội dung này.',
      };
    }
  }

  return {
    eyebrow: 'Lỗi route',
    title: 'Không thể tải trang',
    description: 'Route hiện tại gặp lỗi. Hãy thử tải lại hoặc quay về trang chính.',
  };
}

export function RouteErrorPage() {
  const copy = routeErrorCopy(useRouteError());

  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-6 text-foreground">
      <section className="w-full max-w-xl rounded-lg border bg-surface p-8">
        <p className="mb-2 text-sm text-muted-foreground">{copy.eyebrow}</p>
        <h1 className="text-2xl font-semibold tracking-tight">{copy.title}</h1>
        <p className="mt-3 text-sm text-muted-foreground">{copy.description}</p>
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
