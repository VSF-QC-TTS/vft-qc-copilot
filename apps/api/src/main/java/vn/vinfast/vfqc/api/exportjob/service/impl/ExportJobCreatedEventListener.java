package vn.vinfast.vfqc.api.exportjob.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.vinfast.vfqc.api.exportjob.event.ExportJobCreatedEvent;
import vn.vinfast.vfqc.api.exportjob.service.ExportJobWorker;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportJobCreatedEventListener {

  private final ExportJobWorker worker;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCreated(ExportJobCreatedEvent event) {
    log.info("Export job created event received: exportJobId={}", event.exportJobId());
    worker.run(event.exportJobId());
  }
}
