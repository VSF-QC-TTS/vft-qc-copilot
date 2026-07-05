package vn.vinfast.vfqc.api.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vinfast.vfqc.api.user.dto.AdminCreateUserRequest;
import vn.vinfast.vfqc.api.user.dto.AdminCreateUserResponse;
import vn.vinfast.vfqc.api.user.service.UserAdministrationService;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users", description = "Platform administrator user provisioning APIs")
public class AdminUserController {

  private final UserAdministrationService userAdministrationService;

  @PostMapping
  @Operation(summary = "Create an internal user and send an invite email")
  @ApiResponse(responseCode = "200", description = "User created and invite email sent")
  @ApiResponse(responseCode = "403", description = "Platform administrator role required")
  @ApiResponse(responseCode = "409", description = "Email already exists")
  public AdminCreateUserResponse createUser(
      @Valid @RequestBody AdminCreateUserRequest request, HttpServletRequest servletRequest) {
    return userAdministrationService.createUser(
        request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent"));
  }
}
