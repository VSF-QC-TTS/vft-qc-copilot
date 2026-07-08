package vn.vinfast.vfqc.api.runner.service.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.vinfast.vfqc.api.runner.repository.RunnerEventRepository;
import vn.vinfast.vfqc.api.runner.service.RunnerSequenceService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerSequenceServiceImpl implements RunnerSequenceService {

  private final RunnerEventRepository eventRepository;

  @Override
  public long nextSequence(UUID testRunId) {
    long nextSeq = eventRepository.maxSequenceNo(testRunId) + 1;
    log.debug("Allocated next event sequence number: testRunId={}, nextSeq={}", testRunId, nextSeq);
    return nextSeq;
  }
}
