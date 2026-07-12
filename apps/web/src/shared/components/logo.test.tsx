import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { Logo } from '@/shared/components/logo';

describe('Logo', () => {
  it('renders the VinFast image and text', () => {
    render(<Logo />);
    expect(screen.getByAltText('VinFast')).toBeVisible();
    expect(screen.getByText('QC Copilot')).toBeVisible();
  });
});
