import { cn } from '@/shared/lib/utils';

interface LogoProps {
  className?: string;
}

export function Logo({ className }: LogoProps) {
  return (
    <div className={cn('flex items-center gap-2', className)}>
      <img src="/logo.png" alt="VinFast" className="h-5 w-auto object-contain" />
      <span className="font-semibold tracking-tight text-foreground sm:inline-block">
        QC Copilot
      </span>
    </div>
  );
}
