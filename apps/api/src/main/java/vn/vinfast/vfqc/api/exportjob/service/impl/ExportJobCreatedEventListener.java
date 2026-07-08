package vn.vinfast.vfqc.api.exportjob.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.vinfast.vfqc.api.exportjob.event.ExportJobCreatedEvent;
import vn.vinfast.vfqc.api.exportjob.service.ExportJobWorker;

@Component
@RequiredArgsConstructor
public class ExportJobCreatedEventListener {

  private final ExportJobWorker worker;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCreated(ExportJobCreatedEvent event) {
    worker.run(event.exportJobId());
  }
}
