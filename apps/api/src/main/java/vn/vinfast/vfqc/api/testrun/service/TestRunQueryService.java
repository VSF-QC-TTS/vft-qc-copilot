package vn.vinfast.vfqc.api.testrun.service;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.testrun.dto.CancelTestRunRequest;
import vn.vinfast.vfqc.api.testrun.dto.CreateTestRunRequest;
import vn.vinfast.vfqc.api.testrun.dto.TestRunDetailResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestRunSummaryResponse;

public interface TestRunQueryService {

  /**
   * Lists test runs for a project.
   *
   * <p>Requires project view permission. The query is paged and returns summary data only.
   *
   * @param user current authenticated user
   * @param projectId project identifier
   * @param pageable page request
   * @return paged run summaries
   */
  CollectionResponse<TestRunSummaryResponse> list(CurrentUser user, UUID projectId, Pageable pageable);

  /**
   * Creates a test run with locked sanitized snapshots.
   *
   * <p>Requires project edit permission in the current permission model. The method runs
   * authoritative preflight, writes TestRun/TestRunTarget/TestRunEvaluatorVersion rows in one
   * transaction, and invokes the Phase 8 no-op command boundary after persistence.
   *
   * @param user current authenticated user
   * @param projectId project identifier
   * @param request run selection and options
   * @return created run detail
   */
  TestRunDetailResponse create(CurrentUser user, UUID projectId, CreateTestRunRequest request);

  /**
   * Loads a test run detail.
   *
   * <p>Requires project view permission. The response includes locked sanitized snapshots, targets,
   * and evaluator versions.
   *
   * @param user current authenticated user
   * @param testRunId test run identifier
   * @return run detail
   */
  TestRunDetailResponse get(CurrentUser user, UUID testRunId);

  /**
   * Requests cancellation using the documented state machine.
   *
   * <p>Requires project edit permission in the current permission model. Cancellation is best-effort
   * in Phase 8 because no runner execution exists yet.
   *
   * @param user current authenticated user
   * @param testRunId test run identifier
   * @param request cancellation metadata
   * @return updated run detail
   */
  TestRunDetailResponse cancel(CurrentUser user, UUID testRunId, CancelTestRunRequest request);
}
