package vn.vinfast.vfqc.api.common.mail.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.vinfast.vfqc.api.common.mail.MailService;

/**
 * Listens for {@link MailSendEvent} after the enclosing transaction commits and delegates to
 * {@link MailService} asynchronously. If the transaction rolls back, the event is silently
 * discarded, preventing stale or inconsistent emails.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailSendEventListener {

  private final MailService mailService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMailSend(MailSendEvent event) {
    try {
      mailService.send(event.model());
    } catch (Exception ex) {
      log.error(
          "Failed to send mail after transaction commit: modelType={}",
          event.model().getClass().getSimpleName(),
          ex);
    }
  }
}
