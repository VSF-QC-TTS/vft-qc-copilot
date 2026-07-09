package vn.vinfast.vfqc.api.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import vn.vinfast.vfqc.api.runner.config.RunnerRedisStreams;
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandStatus;
import vn.vinfast.vfqc.api.runner.enums.RunnerCommandType;
import vn.vinfast.vfqc.api.secret.service.SecretEncryptionService;
import vn.vinfast.vfqc.api.support.PostgresIntegrationTestBase;
import vn.vinfast.vfqc.api.support.RunnerSmokeSeedFixture;
import vn.vinfast.vfqc.api.testrun.enums.TestRunStatus;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RunnerSmokeSeedFixtureIntegrationTest extends PostgresIntegrationTestBase {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;
  private final SecretEncryptionService encryptionService;

  @Test
  void seedMinimalRun_CreatesQueuedRunAndPublishesCompactStartCommand() throws Exception {
    var fixture = new RunnerSmokeSeedFixture(this, mockMvc, objectMapper, encryptionService);

    var seeded = fixture.seedMinimalRun();

    assertThat(testRunRepository.findById(seeded.testRunId()).orElseThrow().getStatus())
        .isEqualTo(TestRunStatus.QUEUED);
    assertThat(datasetRowRepository.findByDatasetVersionIdOrderByRowIndexAsc(seeded.datasetVersionId())).hasSize(1);
    assertThat(runnerCommandRepository.findByTestRunIdOrderByCreatedAtAsc(seeded.testRunId()))
        .singleElement()
        .satisfies(
            command -> {
              assertThat(command.getCommandType()).isEqualTo(RunnerCommandType.START_RUN);
              assertThat(command.getStatus()).isEqualTo(RunnerCommandStatus.PUBLISHED);
            });
    var commandPayload = redisTemplate.opsForStream().range(RunnerRedisStreams.COMMANDS, Range.unbounded()).getFirst().getValue();
    assertThat(commandPayload.keySet()).containsExactlyInAnyOrder("commandId", "testRunId", "commandType", "createdAt");
    assertThat(commandPayload.toString())
        .doesNotContain(RunnerSmokeSeedFixture.FAKE_RUNTIME_SECRET)
        .doesNotContain("chatbot.example.test/api");
  }
}
