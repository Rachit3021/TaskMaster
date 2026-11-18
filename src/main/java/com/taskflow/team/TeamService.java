package com.taskflow.team;

import com.taskflow.attachment.AttachmentRepository;
import com.taskflow.attachment.AttachmentStorage;
import com.taskflow.common.dto.PageResponse;
import com.taskflow.common.error.BadRequestException;
import com.taskflow.common.error.ConflictException;
import com.taskflow.common.error.ForbiddenException;
import com.taskflow.common.error.NotFoundException;
import com.taskflow.common.util.AfterCommit;
import com.taskflow.notification.NotificationRepository;
import com.taskflow.notification.NotificationType;
import com.taskflow.notification.NotificationPublisher;
import com.taskflow.project.ProjectRepository;
import com.taskflow.team.dto.CreateTeamRequest;
import com.taskflow.team.dto.InvitationResponse;
import com.taskflow.team.dto.InviteMemberRequest;
import com.taskflow.team.dto.JoinTeamRequest;
import com.taskflow.team.dto.TeamMemberResponse;
import com.taskflow.team.dto.TeamResponse;
import com.taskflow.team.dto.UpdateTeamRequest;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import com.taskflow.user.dto.UserSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class TeamService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;
    private static final long INVITATION_TTL_DAYS = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final InvitationRepository invitationRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TeamMembershipGuard guard;
    private final NotificationRepository notificationRepository;
    private final NotificationPublisher notifications;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorage attachmentStorage;

    public TeamService(TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       InvitationRepository invitationRepository,
                       ProjectRepository projectRepository,
                       UserRepository userRepository,
                       TeamMembershipGuard guard,
                       NotificationRepository notificationRepository,
                       NotificationPublisher notifications,
                       AttachmentRepository attachmentRepository,
                       AttachmentStorage attachmentStorage) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.invitationRepository = invitationRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.guard = guard;
        this.notificationRepository = notificationRepository;
        this.notifications = notifications;
        this.attachmentRepository = attachmentRepository;
        this.attachmentStorage = attachmentStorage;
    }

    @Transactional
    public TeamResponse create(UUID actorId, CreateTeamRequest request) {
        User actor = requireUser(actorId);
        Team team = new Team(request.name().trim(), trimToNull(request.description()), generateInviteCode(), actor);
        teamRepository.save(team);
        teamMemberRepository.save(new TeamMember(team, actor, TeamRole.OWNER));
        return toResponse(team, TeamRole.OWNER);
    }

    @Transactional(readOnly = true)
    public PageResponse<TeamResponse> list(UUID actorId, String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        String normalizedQuery = trimToNull(query);
        Page<Team> teams = normalizedQuery == null
                ? teamRepository.findForUser(actorId, pageable)
                : teamRepository.searchForUser(actorId, normalizedQuery, pageable);
        return PageResponse.from(teams, team -> toResponse(team, requireMembershipRole(team.getId(), actorId)));
    }

    @Transactional(readOnly = true)
    public TeamResponse get(UUID actorId, UUID teamId) {
        TeamMember member = guard.requireMember(teamId, actorId);
        return toResponse(member.getTeam(), member.getRole());
    }

    @Transactional
    public TeamResponse update(UUID actorId, UUID teamId, UpdateTeamRequest request) {
        TeamMember member = guard.requireManager(teamId, actorId);
        Team team = member.getTeam();
        if (request.name() != null) {
            team.setName(request.name().trim());
        }
        if (request.description() != null) {
            team.setDescription(trimToNull(request.description()));
        }
        return toResponse(team, member.getRole());
    }

    @Transactional
    public void delete(UUID actorId, UUID teamId) {
        guard.requireOwner(teamId, actorId);
        Team team = guard.requireTeam(teamId);
        List<String> storedFiles = attachmentRepository.findStoredNamesByTeamId(teamId);
        notificationRepository.deleteByTeamId(teamId);
        teamRepository.delete(team);
        AfterCommit.run(() -> storedFiles.forEach(attachmentStorage::deleteQuietly));
    }

    @Transactional
    public TeamResponse rotateInviteCode(UUID actorId, UUID teamId) {
        TeamMember member = guard.requireManager(teamId, actorId);
        Team team = member.getTeam();
        team.setInviteCode(generateInviteCode());
        return toResponse(team, member.getRole());
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> members(UUID actorId, UUID teamId) {
        guard.requireMember(teamId, actorId);
        return teamMemberRepository.findByTeamIdOrderByJoinedAtAsc(teamId).stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    @Transactional
    public TeamResponse joinByCode(UUID actorId, JoinTeamRequest request) {
        Team team = teamRepository.findByInviteCode(request.inviteCode().trim())
                .orElseThrow(() -> new NotFoundException("Invalid invite code"));
        User actor = requireUser(actorId);
        if (guard.isMember(team.getId(), actorId)) {
            throw new ConflictException("You are already a member of this team");
        }
        teamMemberRepository.save(new TeamMember(team, actor, TeamRole.MEMBER));
        notifications.publish(team.getOwner().getId(), NotificationType.TEAM_MEMBER_JOINED,
                "New team member",
                actor.getName() + " joined \"" + team.getName() + "\" using the invite code",
                team.getId(), null, null);
        return toResponse(team, TeamRole.MEMBER);
    }

    @Transactional
    public TeamResponse acceptInvitation(UUID actorId, String token) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("This invitation is no longer valid");
        }
        if (invitation.isExpired()) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            throw new BadRequestException("This invitation has expired");
        }
        User actor = requireUser(actorId);
        if (!invitation.getEmail().equalsIgnoreCase(actor.getEmail())) {
            throw new ForbiddenException("This invitation was issued to a different email address");
        }
        Team team = invitation.getTeam();
        if (guard.isMember(team.getId(), actorId)) {
            throw new ConflictException("You are already a member of this team");
        }
        teamMemberRepository.save(new TeamMember(team, actor, invitation.getRole()));
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        notifications.publish(invitation.getInvitedBy().getId(), NotificationType.TEAM_MEMBER_JOINED,
                "Invitation accepted",
                actor.getName() + " accepted your invitation to \"" + team.getName() + "\"",
                team.getId(), null, null);
        return toResponse(team, invitation.getRole());
    }

    @Transactional
    public InvitationResponse invite(UUID actorId, UUID teamId, InviteMemberRequest request) {
        TeamMember actorMember = guard.requireManager(teamId, actorId);
        Team team = actorMember.getTeam();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        TeamRole role = request.role() == null ? TeamRole.MEMBER : request.role();
        if (role == TeamRole.OWNER) {
            throw new BadRequestException("A team can only have one owner");
        }
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (guard.isMember(teamId, user.getId())) {
                throw new ConflictException("This user is already a member of the team");
            }
        });
        if (invitationRepository.existsByTeamIdAndEmailIgnoreCaseAndStatus(teamId, email, InvitationStatus.PENDING)) {
            throw new ConflictException("A pending invitation already exists for this email");
        }

        String token = generateInvitationToken();
        Invitation invitation = invitationRepository.save(new Invitation(
                team, email, role, token, actorMember.getUser(),
                Instant.now().plus(INVITATION_TTL_DAYS, ChronoUnit.DAYS)));

        userRepository.findByEmailIgnoreCase(email).ifPresent(invitee ->
                notifications.publish(invitee.getId(), NotificationType.TEAM_INVITATION,
                        "Team invitation",
                        actorMember.getUser().getName() + " invited you to join \"" + team.getName()
                                + "\". Use invitation token " + token + " to accept.",
                        team.getId(), null, null));

        return toInvitationResponse(invitation);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> invitations(UUID actorId, UUID teamId) {
        guard.requireManager(teamId, actorId);
        return invitationRepository.findByTeamIdOrderByCreatedAtDesc(teamId).stream()
                .map(this::toInvitationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> myInvitations(UUID actorId) {
        User actor = requireUser(actorId);
        return invitationRepository
                .findByEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(actor.getEmail(), InvitationStatus.PENDING)
                .stream()
                .filter(invitation -> !invitation.isExpired())
                .map(this::toInvitationResponse)
                .toList();
    }

    @Transactional
    public void revokeInvitation(UUID actorId, UUID teamId, UUID invitationId) {
        guard.requireManager(teamId, actorId);
        Invitation invitation = invitationRepository.findByIdAndTeamId(invitationId, teamId)
                .orElseThrow(() -> NotFoundException.of("Invitation", invitationId));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Only pending invitations can be revoked");
        }
        invitation.setStatus(InvitationStatus.REVOKED);
    }

    @Transactional
    public TeamMemberResponse changeMemberRole(UUID actorId, UUID teamId, UUID userId, TeamRole newRole) {
        guard.requireOwner(teamId, actorId);
        if (newRole == TeamRole.OWNER) {
            throw new BadRequestException("Ownership transfer is not supported; the owner role is unique");
        }
        TeamMember target = guard.requireMember(teamId, userId);
        if (target.getRole() == TeamRole.OWNER) {
            throw new BadRequestException("The owner's role cannot be changed");
        }
        target.setRole(newRole);
        return TeamMemberResponse.from(target);
    }

    @Transactional
    public void removeMember(UUID actorId, UUID teamId, UUID userId) {
        TeamMember actor = guard.requireMember(teamId, actorId);
        TeamMember target = guard.requireMember(teamId, userId);
        if (target.getRole() == TeamRole.OWNER) {
            throw new BadRequestException("The team owner cannot be removed; delete the team instead");
        }
        if (!Objects.equals(actorId, userId)) {
            if (!actor.getRole().canManage()) {
                throw new ForbiddenException("Only team owners and admins can remove members");
            }
            if (target.getRole() == TeamRole.ADMIN && actor.getRole() != TeamRole.OWNER) {
                throw new ForbiddenException("Only the owner can remove another admin");
            }
        }
        teamMemberRepository.delete(target);
    }

    private TeamResponse toResponse(Team team, TeamRole myRole) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                myRole.canManage() ? team.getInviteCode() : null,
                UserSummary.from(team.getOwner()),
                myRole,
                teamMemberRepository.countByTeamId(team.getId()),
                projectRepository.countByTeamId(team.getId()),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }

    private InvitationResponse toInvitationResponse(Invitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getTeam().getId(),
                invitation.getTeam().getName(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.getToken(),
                UserSummary.from(invitation.getInvitedBy()),
                invitation.getExpiresAt(),
                invitation.getCreatedAt()
        );
    }

    private TeamRole requireMembershipRole(UUID teamId, UUID userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .map(TeamMember::getRole)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this team"));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
    }

    private String generateInviteCode() {
        String code;
        do {
            code = randomCode(CODE_LENGTH);
        } while (teamRepository.existsByInviteCode(code));
        return code;
    }

    private static String generateInvitationToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String randomCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
