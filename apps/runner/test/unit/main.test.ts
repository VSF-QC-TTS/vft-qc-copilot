import { describe, expect, it, vi } from 'vitest';

describe('main module', () => {
  it('imports without bootstrapping runner side effects in test mode', async () => {
    const consoleSpy = vi.spyOn(process.stdout, 'write');

    const module = await import('../../src/main.js');

    expect(module.bootstrap).toBeTypeOf('function');
    expect(consoleSpy).not.toHaveBeenCalled();
  });
});
