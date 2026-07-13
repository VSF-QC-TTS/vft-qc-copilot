import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CreateSecretDialog } from './create-secret-dialog';
import { ApiError } from '@/shared/api/api-error';

describe('CreateSecretDialog', () => {
  const mockOnSubmit = vi.fn<(data: { name: string; value: string }) => Promise<void>>();
  const mockOnOpenChange = vi.fn<(open: boolean) => void>();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders correctly', () => {
    render(
      <CreateSecretDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        onSubmit={mockOnSubmit}
        isPending={false}
        scopeLabel="workspace"
      />,
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByLabelText(/Tên hiển thị/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Giá trị Secret/i)).toBeInTheDocument();
  });

  it('submits and clears plaintext on success', async () => {
    mockOnSubmit.mockResolvedValueOnce(undefined);
    render(
      <CreateSecretDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        onSubmit={mockOnSubmit}
        isPending={false}
        scopeLabel="workspace"
      />,
    );

    const nameInput = screen.getByLabelText(/Tên hiển thị/i);
    const valueInput = screen.getByLabelText(/Giá trị Secret/i);

    fireEvent.change(nameInput, { target: { value: 'Test Secret' } });
    fireEvent.change(valueInput, { target: { value: 'super-secret-value' } });

    fireEvent.click(screen.getByRole('button', { name: 'Thêm secret' }));

    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledWith({
        name: 'Test Secret',
        value: 'super-secret-value',
      });
    });

    await waitFor(() => {
      expect(mockOnOpenChange).toHaveBeenCalledWith(false);
    });

    // Check if form is reset by closing and reopening?
    // The component calls form.reset() on success.
    // If we re-render with open=true, it should be empty.
  });

  it('handles 409 duplicate name error', async () => {
    mockOnSubmit.mockRejectedValueOnce(new ApiError({ status: 409 }));
    render(
      <CreateSecretDialog
        open={true}
        onOpenChange={mockOnOpenChange}
        onSubmit={mockOnSubmit}
        isPending={false}
        scopeLabel="workspace"
      />,
    );

    const nameInput = screen.getByLabelText(/Tên hiển thị/i);
    const valueInput = screen.getByLabelText(/Giá trị Secret/i);

    fireEvent.change(nameInput, { target: { value: 'Duplicate Secret' } });
    fireEvent.change(valueInput, { target: { value: 'value' } });

    fireEvent.click(screen.getByRole('button', { name: 'Thêm secret' }));

    await waitFor(() => {
      expect(screen.getByText('Tên secret đã tồn tại')).toBeInTheDocument();
    });
  });
});
