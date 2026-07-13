export type DetailTab = 'overview' | 'targets' | 'evaluators' | 'snapshots' | 'results' | 'matrix';

export const testRunDetailTabs: { value: DetailTab; label: string }[] = [
  { value: 'overview', label: 'Tổng quan' },
  { value: 'targets', label: 'Targets' },
  { value: 'evaluators', label: 'Evaluators' },
  { value: 'snapshots', label: 'Snapshots' },
  { value: 'results', label: 'Machine / Final' },
  { value: 'matrix', label: 'Comparison matrix' },
];

export function parseDetailTab(value: string | null): DetailTab | undefined {
  if (isDetailTab(value)) {
    return value;
  }
  return undefined;
}

export function detailTabFromSearchParams(searchParams: URLSearchParams): DetailTab {
  const explicitTab = parseDetailTab(searchParams.get('tab'));
  if (explicitTab) {
    return explicitTab;
  }

  if (
    searchParams.has('resultId') ||
    searchParams.has('resultMode') ||
    searchParams.has('resultPage')
  ) {
    return 'results';
  }

  if (searchParams.has('matrixMode') || searchParams.has('matrixPage')) {
    return 'matrix';
  }

  return 'overview';
}

function isDetailTab(value: string | null): value is DetailTab {
  return (
    value === 'overview' ||
    value === 'targets' ||
    value === 'evaluators' ||
    value === 'snapshots' ||
    value === 'results' ||
    value === 'matrix'
  );
}
