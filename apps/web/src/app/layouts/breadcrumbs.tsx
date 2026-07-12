import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router';
import { cn } from '@/shared/lib/utils';

export interface BreadcrumbItem {
  label: string;
  href?: string;
}

interface BreadcrumbsProps {
  items: BreadcrumbItem[];
  className?: string;
}

export function Breadcrumbs({ items, className }: BreadcrumbsProps) {
  return (
    <nav aria-label="Breadcrumb" className={cn('flex items-center text-sm', className)}>
      <ol className="flex items-center gap-1.5">
        {items.map((item, index) => {
          const isLast = index === items.length - 1;
          return (
            <li key={item.label} className="flex items-center gap-1.5">
              {item.href && !isLast ? (
                <Link
                  to={item.href}
                  className="text-foreground-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus-ring rounded px-1"
                >
                  {item.label}
                </Link>
              ) : (
                <span
                  className={cn(
                    'px-1',
                    isLast ? 'text-foreground font-medium' : 'text-foreground-muted',
                  )}
                  aria-current={isLast ? 'page' : undefined}
                >
                  {item.label}
                </span>
              )}
              {!isLast && (
                <ChevronRight aria-hidden="true" className="h-4 w-4 text-foreground-disabled" />
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
