package vn.vinfast.vfqc.api.user.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.user.service.UserOnboardingService;
import vn.vinfast.vfqc.api.workspace.entity.Workspace;
import vn.vinfast.vfqc.api.workspace.entity.WorkspaceMember;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceMemberStatus;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceType;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceMemberRepository;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserOnboardingServiceImpl implements UserOnboardingService {

  private final WorkspaceRepository workspaceRepository;
  private final WorkspaceMemberRepository memberRepository;
  private final Clock clock;

  @Override
  @Transactional
  public void ensurePersonalWorkspace(AppUser user) {
    if (workspaceRepository.existsByCreatedByAndTypeAndDeletedAtIsNull(
        user.getId(), WorkspaceType.PERSONAL)) {
      return;
    }
    var now = OffsetDateTime.now(clock);
    try {
      createPersonalWorkspace(user, now);
    } catch (DataIntegrityViolationException ex) {
      if (workspaceRepository.existsByCreatedByAndTypeAndDeletedAtIsNull(
          user.getId(), WorkspaceType.PERSONAL)) {
        log.debug("Personal workspace already exists after concurrent onboarding: userId={}", user.getId());
        return;
      }
      throw ex;
    }
  }

  private void createPersonalWorkspace(AppUser user, OffsetDateTime now) {
    var workspace =
        workspaceRepository.saveAndFlush(
            Workspace.builder()
                .name(personalWorkspaceName(user))
                .nameLookup("personal-" + user.getId())
                .type(WorkspaceType.PERSONAL)
                .createdBy(user.getId())
                .createdAt(now)
                .updatedAt(now)
                .build());
    memberRepository.save(
        WorkspaceMember.builder()
            .workspaceId(workspace.getId())
            .userId(user.getId())
            .role(WorkspaceRole.OWNER)
            .status(WorkspaceMemberStatus.ACTIVE)
            .invitedBy(user.getId())
            .joinedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build());
    log.info("Personal workspace created: workspaceId={} userId={}", workspace.getId(), user.getId());
  }

  private String personalWorkspaceName(AppUser user) {
    var displayName = user.getDisplayName();
    if (displayName == null || displayName.isBlank()) {
      return "Personal Workspace";
    }
    return displayName.trim() + "'s Workspace";
  }
}
