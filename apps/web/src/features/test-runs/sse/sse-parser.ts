import type { ParsedSseEvent } from '@/features/test-runs/sse/test-run-sse.types';

export interface SseParserHandlers {
  onEvent: (event: ParsedSseEvent) => void;
  onComment?: (comment: string) => void;
}

export interface SseStreamParser {
  feed(chunk: Uint8Array | string): void;
  flush(): void;
}

interface EventBuffer {
  id?: string;
  event?: string;
  dataLines: string[];
  retry?: number;
}

export function createSseStreamParser({ onEvent, onComment }: SseParserHandlers): SseStreamParser {
  const decoder = new TextDecoder();
  let pendingText = '';
  let eventBuffer = createEventBuffer();

  function processLine(line: string): void {
    if (line === '') {
      dispatchEvent();
      return;
    }

    if (line.startsWith(':')) {
      const rawComment = line.slice(1);
      onComment?.(rawComment.startsWith(' ') ? rawComment.slice(1) : rawComment);
      return;
    }

    const colonIndex = line.indexOf(':');
    const field = colonIndex === -1 ? line : line.slice(0, colonIndex);
    const rawValue = colonIndex === -1 ? '' : line.slice(colonIndex + 1);
    const value = rawValue.startsWith(' ') ? rawValue.slice(1) : rawValue;

    switch (field) {
      case 'id':
        if (!value.includes('\0')) {
          eventBuffer.id = value;
        }
        break;
      case 'event':
        eventBuffer.event = value;
        break;
      case 'data':
        eventBuffer.dataLines.push(value);
        break;
      case 'retry': {
        const retry = Number(value);
        if (Number.isInteger(retry) && retry >= 0) {
          eventBuffer.retry = retry;
        }
        break;
      }
      default:
        break;
    }
  }

  function dispatchEvent(): void {
    const hasEvent = eventBuffer.dataLines.length > 0 || eventBuffer.event !== undefined;
    if (!hasEvent) {
      eventBuffer = createEventBuffer();
      return;
    }

    onEvent({
      ...(eventBuffer.id !== undefined ? { id: eventBuffer.id } : {}),
      event: eventBuffer.event ?? 'message',
      data: eventBuffer.dataLines.join('\n'),
      ...(eventBuffer.retry !== undefined ? { retry: eventBuffer.retry } : {}),
    });
    eventBuffer = createEventBuffer();
  }

  function consumePendingLines(): void {
    while (true) {
      const nextTerminator = nextLineTerminator(pendingText);
      if (!nextTerminator) {
        return;
      }

      const { index, length } = nextTerminator;
      processLine(pendingText.slice(0, index));
      pendingText = pendingText.slice(index + length);
    }
  }

  return {
    feed(chunk) {
      pendingText += typeof chunk === 'string' ? chunk : decoder.decode(chunk, { stream: true });
      consumePendingLines();
    },
    flush() {
      pendingText += decoder.decode();
      if (pendingText.length > 0) {
        processLine(pendingText);
        pendingText = '';
      }
      dispatchEvent();
    },
  };
}

function createEventBuffer(): EventBuffer {
  return { dataLines: [] };
}

function nextLineTerminator(text: string): { index: number; length: number } | undefined {
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (char === '\n') {
      return { index, length: 1 };
    }
    if (char === '\r') {
      if (index === text.length - 1) {
        return undefined;
      }
      return { index, length: text[index + 1] === '\n' ? 2 : 1 };
    }
  }

  return undefined;
}
