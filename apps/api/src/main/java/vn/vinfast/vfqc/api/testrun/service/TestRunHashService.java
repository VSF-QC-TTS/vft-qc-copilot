package vn.vinfast.vfqc.api.testrun.service;

import java.util.Map;

public interface TestRunHashService {

  /**
   * Calculates a stable SHA-256 hash for a sanitized JSON-like map.
   *
   * <p>The implementation canonicalizes keys through Jackson before hashing. Callers must pass only
   * sanitized snapshots because the hash input may be used for audit/debug comparisons.
   *
   * @param value sanitized JSON-like map
   * @return lowercase hexadecimal SHA-256 hash
   */
  String hash(Map<String, Object> value);
}
