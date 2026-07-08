package vn.vinfast.vfqc.api.runner.service;

import vn.vinfast.vfqc.api.runner.dto.RunnerEventIngestionResponse;
import vn.vinfast.vfqc.api.runner.dto.RunnerEventRequest;

public interface RunnerEventIngestionService {

  /**
   * Persists a sanitized runner event and applies its idempotent TestRun/result side effects.
   *
   * @param request runner event payload
   * @param streamId optional Redis stream id when consumed from Redis
   * @return ingestion outcome
   */
  RunnerEventIngestionResponse ingest(RunnerEventRequest request, String streamId);
}
