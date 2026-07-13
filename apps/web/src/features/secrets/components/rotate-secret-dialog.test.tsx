import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { RotateSecretDialog } from './rotate-secret-dialog';

describe('RotateSecretDialog', () => {
  const mockOnSubmit = vi.fn<(data: { value: string }) => Promise<void>>();
  const mockOnOpenChange = vi.fn<(open: boolean) => void>();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders correctly', () => {
    render(
      <RotateSecretDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        onSubmit={mockOnSubmit}
        isPending={false}
        secretName="Test Secret"
      />,
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByLabelText(/Giá trị mới/i)).toBeInTheDocument();
    expect(screen.getByText(/Test Secret/)).toBeInTheDocument();
  });

  it('submits and clears plaintext on success', async () => {
    mockOnSubmit.mockResolvedValueOnce(undefined);
    render(
      <RotateSecretDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        onSubmit={mockOnSubmit}
        isPending={false}
        secretName="Test Secret"
      />,
    );

    const valueInput = screen.getByLabelText(/Giá trị mới/i);
    fireEvent.change(valueInput, { target: { value: 'new-secret-value' } });

    fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledWith({
        value: 'new-secret-value',
      });
    });

    await waitFor(() => {
      expect(mockOnOpenChange).toHaveBeenCalledWith(false);
    });
  });
});
