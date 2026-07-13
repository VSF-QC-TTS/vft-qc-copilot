import { describe, expect, it } from 'vitest';
import { secretResponseSchema, secretCollectionSchema } from '../types/secret';

describe('Secrets API Types', () => {
  it('validates a valid SecretResponse', () => {
    const data = {
      id: '123e4567-e89b-12d3-a456-426614174000',
      scope: 'PERSONAL',
      ownerUserId: '123e4567-e89b-12d3-a456-426614174001',
      ownerUser: {
        id: '123e4567-e89b-12d3-a456-426614174001',
        email: 'test@example.com',
        name: 'Test',
      },
      workspaceId: null,
      name: 'Test Secret',
      maskedPreview: 'sec***123',
      status: 'ACTIVE',
      createdAt: '2026-07-13T00:00:00Z',
      updatedAt: '2026-07-13T00:00:00Z',
    };

    const result = secretResponseSchema.safeParse(data);
    expect(result.success).toBe(true);
  });

  it('validates a valid SecretCollection', () => {
    const data = {
      items: [],
      page: {
        limit: 10,
        nextCursor: null,
        hasMore: false,
      },
    };

    const result = secretCollectionSchema.safeParse(data);
    expect(result.success).toBe(true);
  });

  it('rejects invalid scope', () => {
    const data = {
      id: '123e4567-e89b-12d3-a456-426614174000',
      scope: 'INVALID',
      ownerUserId: '123e4567-e89b-12d3-a456-426614174001',
      ownerUser: {
        id: '123e4567-e89b-12d3-a456-426614174001',
        email: 'test@example.com',
        name: 'Test',
      },
      name: 'Test Secret',
      maskedPreview: 'sec***123',
      status: 'ACTIVE',
      createdAt: '2026-07-13T00:00:00Z',
      updatedAt: '2026-07-13T00:00:00Z',
    };

    const result = secretResponseSchema.safeParse(data);
    expect(result.success).toBe(false);
  });
});
