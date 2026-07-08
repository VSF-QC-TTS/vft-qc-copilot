package vn.vinfast.vfqc.api.testrun.service.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.dto.CreateTestRunRequest;
import vn.vinfast.vfqc.api.testrun.dto.RunPreflightRequest;
import vn.vinfast.vfqc.api.testrun.dto.RunPreflightResponse;
import vn.vinfast.vfqc.api.testrun.service.TestRunPreflightService;
import vn.vinfast.vfqc.api.testrun.service.TestRunSnapshotService;

@Service
@RequiredArgsConstructor
public class TestRunPreflightServiceImpl implements TestRunPreflightService {

  private final TestRunSnapshotService snapshotService;

  @Override
  @Transactional(readOnly = true)
  public RunPreflightResponse preflight(CurrentUser user, UUID projectId, RunPreflightRequest request) {
    var createRequest =
        new CreateTestRunRequest(
            request.datasetId(), null, request.targets(), request.evaluators(), request.options());
    var inspection = snapshotService.inspect(user, projectId, createRequest);
    return new RunPreflightResponse(
        inspection.blockers().isEmpty(),
        inspection.blockers(),
        inspection.warnings(),
        inspection.counts());
  }
}
