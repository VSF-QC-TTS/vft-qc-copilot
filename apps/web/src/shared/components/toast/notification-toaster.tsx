import { useEffect, useState } from 'react';
import { Toaster } from 'sonner';

import { useTheme } from '@/app/providers/theme-provider';

export function NotificationToaster() {
  const { theme } = useTheme();
  const [position, setPosition] = useState<'top-right' | 'top-center'>('top-right');

  useEffect(() => {
    const narrowScreen = window.matchMedia('(max-width: 767px)');
    const updatePosition = () => setPosition(narrowScreen.matches ? 'top-center' : 'top-right');

    updatePosition();
    narrowScreen.addEventListener('change', updatePosition);
    return () => narrowScreen.removeEventListener('change', updatePosition);
  }, []);

  return (
    <Toaster
      position={position}
      visibleToasts={3}
      closeButton
      theme={theme}
      richColors={false}
      toastOptions={{
        closeButtonAriaLabel: 'Đóng thông báo',
      }}
      containerAriaLabel="Thông báo"
    />
  );
}
