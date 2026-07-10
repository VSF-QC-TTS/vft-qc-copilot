package vn.vinfast.vfqc.api.user.repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.vinfast.vfqc.api.user.entity.AppUser;

public interface UserRepository extends JpaRepository<AppUser, UUID> {

  Optional<AppUser> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

  Optional<AppUser> findByIdAndDeletedAtIsNull(UUID id);

  List<AppUser> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

  boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
					SELECT user
					FROM AppUser user
					WHERE user.id = :userId
						AND user.deletedAt IS NULL
					""")
  Optional<AppUser> findForUpdateById(@Param("userId") UUID userId);
}
