package vn.vinfast.vfqc.api.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.vinfast.vfqc.api.user.entity.AppUser;
import vn.vinfast.vfqc.api.workspace.entity.Workspace;
import vn.vinfast.vfqc.api.workspace.entity.WorkspaceMember;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceRole;
import vn.vinfast.vfqc.api.workspace.enums.WorkspaceType;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceMemberRepository;
import vn.vinfast.vfqc.api.workspace.repository.WorkspaceRepository;

@ExtendWith(MockitoExtension.class)
class UserOnboardingServiceImplTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-07T07:00:00Z"), ZoneOffset.UTC);

  @Mock private WorkspaceRepository workspaceRepository;
  @Mock private WorkspaceMemberRepository memberRepository;

  private UserOnboardingServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new UserOnboardingServiceImpl(workspaceRepository, memberRepository, CLOCK);
  }

  @Test
  void ensurePersonalWorkspace_MissingWorkspace_CreatesOwnerMembership() {
    var user = user();
    var workspaceId = UUID.randomUUID();
    when(workspaceRepository.existsByCreatedByAndTypeAndDeletedAtIsNull(
            user.getId(), WorkspaceType.PERSONAL))
        .thenReturn(false);
    when(workspaceRepository.saveAndFlush(any(Workspace.class)))
        .thenAnswer(
            invocation -> {
              var workspace = invocation.<Workspace>getArgument(0);
              workspace.setId(workspaceId);
              return workspace;
            });
    var workspaceCaptor = ArgumentCaptor.forClass(Workspace.class);
    var memberCaptor = ArgumentCaptor.forClass(WorkspaceMember.class);

    service.ensurePersonalWorkspace(user);

    verify(workspaceRepository).saveAndFlush(workspaceCaptor.capture());
    assertThat(workspaceCaptor.getValue().getType()).isEqualTo(WorkspaceType.PERSONAL);
    assertThat(workspaceCaptor.getValue().getCreatedBy()).isEqualTo(user.getId());
    verify(memberRepository).save(memberCaptor.capture());
    assertThat(memberCaptor.getValue().getWorkspaceId()).isEqualTo(workspaceId);
    assertThat(memberCaptor.getValue().getUserId()).isEqualTo(user.getId());
    assertThat(memberCaptor.getValue().getRole()).isEqualTo(WorkspaceRole.OWNER);
  }

  @Test
  void ensurePersonalWorkspace_ExistingWorkspace_DoesNothing() {
    var user = user();
    when(workspaceRepository.existsByCreatedByAndTypeAndDeletedAtIsNull(
            user.getId(), WorkspaceType.PERSONAL))
        .thenReturn(true);

    service.ensurePersonalWorkspace(user);

    verify(workspaceRepository, never()).saveAndFlush(any());
    verify(memberRepository, never()).save(any());
  }

  private AppUser user() {
    return AppUser.builder()
        .id(UUID.randomUUID())
        .email("user@vinfast.vn")
        .displayName("Test User")
        .build();
  }
}
