package vn.vinfast.vfqc.api.runner.config;

public final class RunnerRedisStreams {

  public static final String COMMANDS = "vfqc:runs:commands";
  public static final String EVENTS = "vfqc:runs:events";
  public static final String COMMANDS_DLQ = "vfqc:runs:commands:dlq";
  public static final String EVENTS_DLQ = "vfqc:runs:events:dlq";

  private RunnerRedisStreams() {}
}
