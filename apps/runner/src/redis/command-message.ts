import { z } from 'zod';

import { RUNNER_COMMAND_TYPES, type RunnerCommandType } from '../backend/dto/runner-api.dto.js';

export type { RunnerCommandType } from '../backend/dto/runner-api.dto.js';

export const RunnerCommandMessageSchema = z.object({
  commandId: z.uuid(),
  testRunId: z.uuid(),
  commandType: z.enum(RUNNER_COMMAND_TYPES),
  createdAt: z.string().min(1),
});

export type RunnerCommandMessage = z.infer<typeof RunnerCommandMessageSchema>;

export interface RunnerCommandDelivery {
  readonly streamMessageId: string;
  readonly message: RunnerCommandMessage;
}

export type RedisCommandFields = Readonly<Record<string, string>> | readonly string[];

export function parseRunnerCommandMessage(message: unknown): RunnerCommandMessage {
  return RunnerCommandMessageSchema.parse(message);
}

export function parseRunnerCommandFields(fields: RedisCommandFields): RunnerCommandMessage {
  return parseRunnerCommandMessage(fieldsToRecord(fields));
}

function fieldsToRecord(fields: RedisCommandFields): Record<string, string> {
  if (!isFieldArray(fields)) {
    return { ...fields };
  }

  const record: Record<string, string> = {};
  for (let index = 0; index < fields.length; index += 2) {
    const key = fields[index];
    const value = fields[index + 1];
    if (key === undefined || value === undefined) {
      throw new Error('Redis command fields must contain key/value pairs');
    }
    record[key] = value;
  }
  return record;
}

function isFieldArray(fields: RedisCommandFields): fields is readonly string[] {
  return Array.isArray(fields);
}

export function isRunnerCommandType(value: unknown): value is RunnerCommandType {
  return typeof value === 'string' && RUNNER_COMMAND_TYPES.includes(value as RunnerCommandType);
}
