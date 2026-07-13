import { describe, expect, it } from 'vitest';

import { createSseStreamParser } from '@/features/test-runs/sse/sse-parser';
import type { ParsedSseEvent } from '@/features/test-runs/sse/test-run-sse.types';

describe('SSE parser', () => {
  it('parses one complete event with id, event, data, retry, and ignored fields', () => {
    const { events } = parseChunks([
      'id: 1\nevent: RUN_STARTED\nretry: 1000\nunknown: ignored\ndata: {"ok":true}\n\n',
    ]);

    expect(events).toEqual([{ id: '1', event: 'RUN_STARTED', data: '{"ok":true}', retry: 1000 }]);
  });

  it('parses events split across chunks and multiple events in one chunk', () => {
    const { events } = parseChunks([
      'id:',
      '1\nevent:RUN_STARTED\ndata:{}\n\nid:2\nevent:RUN_PROGRESS\ndata:{"done":1}\n\n',
    ]);

    expect(events).toEqual([
      { id: '1', event: 'RUN_STARTED', data: '{}' },
      { id: '2', event: 'RUN_PROGRESS', data: '{"done":1}' },
    ]);
  });

  it('supports multiline data, empty data, CRLF, LF, and trailing partial lines', () => {
    const { events } = parseChunks(['event: message\r\ndata: first\r\ndata:\ndata: third']);

    expect(events).toEqual([{ event: 'message', data: 'first\n\nthird' }]);
  });

  it('emits heartbeat comments without business events', () => {
    const { events, comments } = parseChunks([': ping\n\n']);

    expect(comments).toEqual(['ping']);
    expect(events).toEqual([]);
  });

  it('handles UTF-8 characters split across byte chunks', () => {
    const encoder = new TextEncoder();
    const bytes = encoder.encode('id: 1\nevent: RUN_PROGRESS\ndata: {"label":"Đang chạy"}\n\n');
    const { events } = parseChunks([bytes.slice(0, 42), bytes.slice(42)]);

    expect(events).toEqual([{ id: '1', event: 'RUN_PROGRESS', data: '{"label":"Đang chạy"}' }]);
  });
});

function parseChunks(chunks: ReadonlyArray<string | Uint8Array>) {
  const events: ParsedSseEvent[] = [];
  const comments: string[] = [];
  const parser = createSseStreamParser({
    onEvent: (event) => events.push(event),
    onComment: (comment) => comments.push(comment),
  });

  chunks.forEach((chunk) => parser.feed(chunk));
  parser.flush();

  return { events, comments };
}
