export function HomePage() {
  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-6 text-foreground">
      <section className="w-full max-w-xl border border-border bg-surface p-8">
        <p className="mb-2 text-sm text-foreground-muted">Nền tảng kiểm thử chất lượng</p>
        <h1 className="text-2xl font-semibold tracking-tight">VinFast QC Copilot</h1>
        <p className="mt-3 text-sm text-foreground-secondary">
          Frontend đang được khởi tạo theo contract của hệ thống.
        </p>
      </section>
    </main>
  );
}
