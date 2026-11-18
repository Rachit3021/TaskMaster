package com.taskflow.user;

import com.taskflow.common.dto.PageResponse;
import com.taskflow.security.SecurityUtils;
import com.taskflow.user.dto.ChangePasswordRequest;
import com.taskflow.user.dto.UpdateProfileRequest;
import com.taskflow.user.dto.UserResponse;
import com.taskflow.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Authenticated user profile operations")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user's profile")
    public UserResponse me() {
        return userService.getProfile(SecurityUtils.currentUserId());
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the current user's profile")
    public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(SecurityUtils.currentUserId(), request);
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change password and sign out other sessions")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(SecurityUtils.currentUserId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "Search teammates shared across your teams")
    public PageResponse<UserSummary> search(
            @RequestParam("q") @Size(min = 2, max = 80, message = "q must be between 2 and 80 characters") String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return userService.searchTeamMates(SecurityUtils.currentUserId(), query, page, size);
    }
}
