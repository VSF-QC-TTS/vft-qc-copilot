package vn.vinfast.vfqc.api.target.parser;

import java.util.Map;

public record ParsedCurlRequest(
    String method,
    String url,
    Map<String, String> headers,
    Map<String, String> queryParams,
    String body) {}
