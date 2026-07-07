package vn.vinfast.vfqc.api.testrun.dto;

import java.util.List;

public record RunPreflightResponse(
    boolean runnable,
    List<PreflightMessageResponse> blockers,
    List<PreflightMessageResponse> warnings,
    Counts counts) {

  public record Counts(int rows, int targets, int evaluators) {}
}
