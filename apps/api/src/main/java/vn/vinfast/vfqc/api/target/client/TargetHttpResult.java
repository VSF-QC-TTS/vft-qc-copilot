package vn.vinfast.vfqc.api.target.client;

import java.util.Map;

public record TargetHttpResult(
    int statusCode,
    Map<String, Object> responseHeaders,
    String responseBody,
    int latencyMs,
    String errorMessage) {}
