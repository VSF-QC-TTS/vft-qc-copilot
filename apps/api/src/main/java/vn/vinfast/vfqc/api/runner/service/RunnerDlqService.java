package vn.vinfast.vfqc.api.runner.service;

import java.util.Map;

public interface RunnerDlqService {

  /**
   * Sends a sanitized failed runner message to its dead-letter stream.
   *
   * @param streamName dead-letter stream name
   * @param message original compact message
   * @param error sanitized error summary
   */
  void send(String streamName, Map<String, String> message, String error);
}
