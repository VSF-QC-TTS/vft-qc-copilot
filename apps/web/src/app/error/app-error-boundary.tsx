import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button } from '@/shared/components/ui/button';

interface AppErrorBoundaryProps {
  children: ReactNode;
}

interface AppErrorBoundaryState {
  hasError: boolean;
}

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(_error: Error, _errorInfo: ErrorInfo): void {
    // Intentionally do not log error objects here. They may contain request payloads or tokens.
  }

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    return (
      <main className="flex min-h-svh items-center justify-center bg-background px-6 text-foreground">
        <section className="w-full max-w-xl rounded-lg border bg-surface p-8">
          <p className="mb-2 text-sm text-danger">Lỗi giao diện</p>
          <h1 className="text-2xl font-semibold tracking-tight">Không thể hiển thị trang</h1>
          <p className="mt-3 text-sm text-muted-foreground">
            Ứng dụng gặp lỗi khi render. Hãy tải lại trang hoặc quay về trang chính.
          </p>
          <div className="mt-6 flex flex-wrap gap-2">
            <Button type="button" onClick={() => this.setState({ hasError: false })}>
              Thử lại
            </Button>
            <Button asChild variant="outline">
              <a href="/">Về trang chính</a>
            </Button>
          </div>
        </section>
      </main>
    );
  }
}
