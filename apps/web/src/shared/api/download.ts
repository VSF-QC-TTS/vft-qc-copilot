function sanitizeFilename(filename: string): string {
  const finalSegment = filename.split(/[\\/]/).at(-1) ?? '';
  const sanitized = Array.from(finalSegment)
    .filter((character) => {
      const codePoint = character.codePointAt(0);
      return codePoint !== undefined && codePoint >= 32 && codePoint !== 127;
    })
    .join('')
    .trim();
  return sanitized || 'download';
}

export function filenameFromContentDisposition(header: string | null): string | undefined {
  if (!header) {
    return undefined;
  }

  const encodedMatch = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (encodedMatch?.[1]) {
    try {
      return sanitizeFilename(decodeURIComponent(encodedMatch[1]));
    } catch {
      return sanitizeFilename(encodedMatch[1]);
    }
  }

  const plainMatch = /filename="?([^";]+)"?/i.exec(header);
  return plainMatch?.[1] ? sanitizeFilename(plainMatch[1]) : undefined;
}

export function downloadBlob(blob: Blob, filename: string): void {
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = sanitizeFilename(filename);
  anchor.hidden = true;
  document.body.append(anchor);

  try {
    anchor.click();
  } finally {
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
  }
}
