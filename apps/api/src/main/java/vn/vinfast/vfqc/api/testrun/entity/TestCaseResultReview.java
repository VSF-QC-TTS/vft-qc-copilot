package vn.vinfast.vfqc.api.testrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vinfast.vfqc.api.testrun.enums.ReviewStatus;
import vn.vinfast.vfqc.api.testrun.enums.TestCaseResultStatus;

@Entity
@Table(name = "test_case_result_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseResultReview {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "test_case_result_id", nullable = false)
  private Long testCaseResultId;

  @Column(name = "reviewer_id", nullable = false)
  private UUID reviewerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false)
  private ReviewStatus reviewStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "override_status")
  private TestCaseResultStatus overrideStatus;

  @Column(name = "override_score")
  private BigDecimal overrideScore;

  private String note;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
