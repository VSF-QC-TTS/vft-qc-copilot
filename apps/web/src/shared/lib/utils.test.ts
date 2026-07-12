import { cn } from '@/shared/lib/utils';
import { describe, expect, it } from 'vitest';

describe('cn', () => {
  it('merges conditional and conflicting Tailwind classes', () => {
    expect(cn('px-2', undefined, 'px-4')).toBe('px-4');
  });
});
