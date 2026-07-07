package vn.vinfast.vfqc.api.testrun.dto;

import java.util.List;
import vn.vinfast.vfqc.api.common.api.PageInfo;

public record TestRunResultPageResponse(List<TestCaseResultDetailResponse> items, PageInfo page) {}
