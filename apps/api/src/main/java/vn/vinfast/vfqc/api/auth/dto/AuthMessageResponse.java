package vn.vinfast.vfqc.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthMessageResponse(@Schema(example = "Request accepted") String message) {}
