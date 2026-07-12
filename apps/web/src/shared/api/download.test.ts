import { afterEach, describe, expect, it, vi } from 'vitest';

import { downloadBlob, filenameFromContentDisposition } from '@/shared/api/download';

describe('download', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('reads and sanitizes a UTF-8 filename', () => {
    expect(
      filenameFromContentDisposition("attachment; filename*=UTF-8''K%E1%BA%BFt-qu%E1%BA%A3.xlsx"),
    ).toBe('Kết-quả.xlsx');
    expect(filenameFromContentDisposition('attachment; filename="../secret.txt"')).toBe(
      'secret.txt',
    );
  });

  it('creates and revokes a temporary object URL', () => {
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:download');
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);

    downloadBlob(new Blob(['export']), 'result.xlsx');

    expect(createObjectURL).toHaveBeenCalledOnce();
    expect(click).toHaveBeenCalledOnce();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:download');
  });
});
