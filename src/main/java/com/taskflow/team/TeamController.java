package com.taskflow.team;

import com.taskflow.common.dto.PageResponse;
import com.taskflow.security.SecurityUtils;
import com.taskflow.team.dto.CreateTeamRequest;
import com.taskflow.team.dto.InvitationResponse;
import com.taskflow.team.dto.InviteMemberRequest;
import com.taskflow.team.dto.JoinTeamRequest;
import com.taskflow.team.dto.TeamMemberResponse;
import com.taskflow.team.dto.TeamResponse;
import com.taskflow.team.dto.UpdateMemberRoleRequest;
import com.taskflow.team.dto.UpdateTeamRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Team membership, invitations and roles")
@Validated
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a team (the caller becomes owner)")
    public TeamResponse create(@Valid @RequestBody CreateTeamRequest request) {
        return teamService.create(SecurityUtils.currentUserId(), request);
    }

    @GetMapping
    @Operation(summary = "List teams the current user belongs to")
    public PageResponse<TeamResponse> list(
            @RequestParam(required = false) @Size(max = 120) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return teamService.list(SecurityUtils.currentUserId(), q, page, size);
    }

    @GetMapping("/{teamId}")
    @Operation(summary = "Get a team by id")
    public TeamResponse get(@PathVariable UUID teamId) {
        return teamService.get(SecurityUtils.currentUserId(), teamId);
    }

    @PatchMapping("/{teamId}")
    @Operation(summary = "Update a team (owner/admin)")
    public TeamResponse update(@PathVariable UUID teamId, @Valid @RequestBody UpdateTeamRequest request) {
        return teamService.update(SecurityUtils.currentUserId(), teamId, request);
    }

    @DeleteMapping("/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a team and everything in it (owner only)")
    public ResponseEntity<Void> delete(@PathVariable UUID teamId) {
        teamService.delete(SecurityUtils.currentUserId(), teamId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{teamId}/invite-code/rotate")
    @Operation(summary = "Rotate the shareable invite code (owner/admin)")
    public TeamResponse rotateInviteCode(@PathVariable UUID teamId) {
        return teamService.rotateInviteCode(SecurityUtils.currentUserId(), teamId);
    }

    @PostMapping("/join")
    @Operation(summary = "Join a team using its invite code")
    public TeamResponse join(@Valid @RequestBody JoinTeamRequest request) {
        return teamService.joinByCode(SecurityUtils.currentUserId(), request);
    }

    @PostMapping("/{teamId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invite an email address to the team (owner/admin)")
    public InvitationResponse invite(@PathVariable UUID teamId,
                                     @Valid @RequestBody InviteMemberRequest request) {
        return teamService.invite(SecurityUtils.currentUserId(), teamId, request);
    }

    @GetMapping("/{teamId}/invitations")
    @Operation(summary = "List team invitations (owner/admin)")
    public List<InvitationResponse> invitations(@PathVariable UUID teamId) {
        return teamService.invitations(SecurityUtils.currentUserId(), teamId);
    }

    @GetMapping("/invitations/mine")
    @Operation(summary = "List invitations addressed to the current user")
    public List<InvitationResponse> myInvitations() {
        return teamService.myInvitations(SecurityUtils.currentUserId());
    }

    @PostMapping("/invitations/{token}/accept")
    @Operation(summary = "Accept an email invitation by token")
    public TeamResponse acceptInvitation(@PathVariable String token) {
        return teamService.acceptInvitation(SecurityUtils.currentUserId(), token);
    }

    @DeleteMapping("/{teamId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a pending invitation (owner/admin)")
    public ResponseEntity<Void> revokeInvitation(@PathVariable UUID teamId, @PathVariable UUID invitationId) {
        teamService.revokeInvitation(SecurityUtils.currentUserId(), teamId, invitationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{teamId}/members")
    @Operation(summary = "List team members")
    public List<TeamMemberResponse> members(@PathVariable UUID teamId) {
        return teamService.members(SecurityUtils.currentUserId(), teamId);
    }

    @PatchMapping("/{teamId}/members/{userId}")
    @Operation(summary = "Change a member's role (owner only)")
    public TeamMemberResponse changeRole(@PathVariable UUID teamId,
                                         @PathVariable UUID userId,
                                         @Valid @RequestBody UpdateMemberRoleRequest request) {
        return teamService.changeMemberRole(SecurityUtils.currentUserId(), teamId, userId, request.role());
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member, or leave the team when userId is the caller")
    public ResponseEntity<Void> removeMember(@PathVariable UUID teamId, @PathVariable UUID userId) {
        teamService.removeMember(SecurityUtils.currentUserId(), teamId, userId);
        return ResponseEntity.noContent().build();
    }
}
