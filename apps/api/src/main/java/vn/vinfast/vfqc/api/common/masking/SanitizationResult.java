package vn.vinfast.vfqc.api.common.masking;

import java.util.List;

public record SanitizationResult<T>(T value, List<String> findings) {}
