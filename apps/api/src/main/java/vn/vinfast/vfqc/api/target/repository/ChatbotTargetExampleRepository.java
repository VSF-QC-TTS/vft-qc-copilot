package vn.vinfast.vfqc.api.target.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.target.entity.ChatbotTargetExample;

public interface ChatbotTargetExampleRepository extends JpaRepository<ChatbotTargetExample, Long> {

  List<ChatbotTargetExample> findByChatbotTargetIdOrderByCreatedAtDesc(UUID chatbotTargetId);
}
