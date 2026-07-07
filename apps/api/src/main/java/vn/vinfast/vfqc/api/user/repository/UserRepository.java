package vn.vinfast.vfqc.api.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vinfast.vfqc.api.user.entity.AppUser;

public interface UserRepository extends JpaRepository<AppUser, UUID> {

  Optional<AppUser> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

  List<AppUser> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

  boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);
}
