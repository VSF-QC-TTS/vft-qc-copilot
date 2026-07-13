import { useState } from 'react';
import { Play } from 'lucide-react';
import { Button } from '@/shared/components/ui/button';
import { Textarea } from '@/shared/components/ui/textarea';
import { notifyError, notifySuccess } from '@/shared/lib/notifications';
import type {
  ChatbotTargetExample,
  ChatbotTargetReadiness,
} from '@/features/chatbot-targets/types/chatbot-target';

export interface TargetTestPanelProps {
  readiness: ChatbotTargetReadiness | undefined;
  examples: ChatbotTargetExample[] | undefined;
  canEdit: boolean;
  isTesting: boolean;
  onTest: (datasetValues: Record<string, string>) => Promise<void>;
}

export function TargetTestPanel({
  readiness,
  examples,
  canEdit,
  isTesting,
  onTest,
}: TargetTestPanelProps) {
  const [datasetValuesText, setDatasetValuesText] = useState('question: Xin chào');

  const handleTest = async () => {
    try {
      await onTest(parseKeyValueLines(datasetValuesText));
      notifySuccess('Đã chạy test target');
    } catch {
      notifyError('Không thể chạy test target');
    }
  };

  return (
    <div className="space-y-6">
      <section className="rounded-lg border p-5">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h2 className="text-base font-semibold">Readiness</h2>
            <p className="text-sm text-muted-foreground">
              ACTIVE readiness đến từ backend và là điều kiện cho TestRun.
            </p>
          </div>
          <span
            className={
              readiness?.ready
                ? 'rounded-full bg-success-soft px-2.5 py-0.5 text-xs font-medium text-success'
                : 'rounded-full bg-warning-soft px-2.5 py-0.5 text-xs font-medium text-warning'
            }
          >
            {readiness?.ready ? 'Sẵn sàng' : 'Chưa sẵn sàng'}
          </span>
        </div>
        {readiness && readiness.blockers.length > 0 && (
          <ul className="mt-4 space-y-2 text-sm text-warning">
            {readiness.blockers.map((blocker) => (
              <li key={`${blocker.code}-${blocker.message}`}>
                <span className="font-medium">{blocker.code}</span>: {blocker.message}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="rounded-lg border p-5">
        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div>
            <h2 className="text-base font-semibold">Test target</h2>
            <p className="text-sm text-muted-foreground">
              Dataset values dùng để test, không nhập secret plaintext.
            </p>
          </div>
          <Button type="button" onClick={() => void handleTest()} disabled={!canEdit || isTesting}>
            <Play className="mr-2 h-4 w-4" />
            Chạy test
          </Button>
        </div>
        <Textarea
          value={datasetValuesText}
          onChange={(event) => setDatasetValuesText(event.target.value)}
          disabled={!canEdit || isTesting}
          placeholder="question: Xin chào"
          className="mt-4 min-h-[100px] font-mono text-xs"
        />
      </section>

      <section className="rounded-lg border p-5">
        <h2 className="text-base font-semibold">Ví dụ đã sanitize</h2>
        <div className="mt-4 space-y-4">
          {!examples?.length ? (
            <p className="text-sm text-muted-foreground">Chưa có ví dụ test nào.</p>
          ) : (
            examples.map((example) => (
              <div key={example.id} className="rounded-md bg-muted p-3 text-sm">
                <div className="mb-2 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                  <span>HTTP {example.responseStatusCode ?? '-'}</span>
                  <span>{example.latencyMs ?? '-'} ms</span>
                  <span>{new Date(example.createdAt).toLocaleString('vi-VN')}</span>
                </div>
                {example.errorMessage && (
                  <p className="mb-2 text-destructive">Lỗi: {example.errorMessage}</p>
                )}
                <pre className="max-h-72 overflow-auto whitespace-pre-wrap break-words text-xs">
                  {JSON.stringify(
                    {
                      request: example.request,
                      responseBody: example.responseBody,
                      extractedOutput: example.extractedOutput,
                    },
                    null,
                    2,
                  )}
                </pre>
              </div>
            ))
          )}
        </div>
      </section>
    </div>
  );
}

function parseKeyValueLines(value: string): Record<string, string> {
  return Object.fromEntries(
    value
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const separatorIndex = line.indexOf(':');
        if (separatorIndex <= 0) {
          throw new Error('Dataset values cần có dạng column: value');
        }
        return [
          line.slice(0, separatorIndex).trim(),
          line.slice(separatorIndex + 1).trim(),
        ] as const;
      }),
  );
}
