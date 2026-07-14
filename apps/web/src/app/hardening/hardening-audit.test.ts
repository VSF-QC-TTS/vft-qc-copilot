import { describe, expect, it } from 'vitest';

const runtimeSourceModules = import.meta.glob<string>('/src/**/*.{ts,tsx}', {
  eager: true,
  import: 'default',
  query: '?raw',
});

const TEST_FILE_PATTERN = /\.(test|spec)\.tsx?$/;

const forbiddenPatterns = [
  { name: 'console logging', pattern: /\bconsole\.(log|debug|info|warn|error)\s*\(/ },
  { name: 'deprecated TanStack cacheTime', pattern: /\bcacheTime\s*:/ },
  { name: 'deprecated keepPreviousData flag', pattern: /\bkeepPreviousData\s*:\s*true\b/ },
  { name: 'deprecated useErrorBoundary option', pattern: /\buseErrorBoundary\s*:/ },
  { name: 'legacy Hydrate component', pattern: /<Hydrate\b/ },
  { name: 'runtime raw hex color', pattern: /#[0-9a-fA-F]{3,8}\b/ },
  { name: 'runtime heavy shadow utility', pattern: /\bshadow-(md|lg|xl|2xl)\b/ },
  {
    name: 'sensitive URL search param',
    pattern:
      /(?:setSearchParams|URLSearchParams|searchParams\.set)\([^)]*(?:accessToken|refreshToken|password|secretValue|rawCurl|apiKey)/i,
  },
];

describe('frontend hardening audit', () => {
  it('keeps runtime source free from sensitive logging and deprecated query APIs', () => {
    const violations = runtimeSourceFiles()
      .flatMap(([filePath, content]) =>
        forbiddenPatterns
          .filter(({ pattern }) => pattern.test(content))
          .map(({ name }) => `${relativeSourcePath(filePath)}: ${name}`),
      )
      .sort();

    expect(violations).toEqual([]);
  });
});

function runtimeSourceFiles(): Array<[string, string]> {
  return Object.entries(runtimeSourceModules).filter(
    ([filePath]) =>
      !TEST_FILE_PATTERN.test(filePath) && !filePath.startsWith('/src/shared/components/ui/'),
  );
}

function relativeSourcePath(filePath: string): string {
  return filePath.replace(/^\/src\//, '');
}
