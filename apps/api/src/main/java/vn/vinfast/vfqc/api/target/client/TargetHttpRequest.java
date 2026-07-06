package vn.vinfast.vfqc.api.target.client;

import java.util.Map;

public record TargetHttpRequest(
    String method,
    String url,
    Map<String, String> headers,
    Map<String, String> queryParams,
    String body) {}
