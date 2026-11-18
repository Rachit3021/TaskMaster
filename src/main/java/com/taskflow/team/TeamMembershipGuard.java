package com.taskflow.team;

import com.taskflow.common.error.ForbiddenException;
import com.taskflow.common.error.NotFoundException;
import com.taskflow.user.User;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Centralised team membership / role checks. */
@Component
public class TeamMembershipGuard {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public TeamMembershipGuard(TeamRepository teamRepository, TeamMemberRepository teamMemberRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    public Team requireTeam(UUID teamId) {
        return teamRepository.findById(teamId).orElseThrow(() -> NotFoundException.of("Team", teamId));
    }

    public TeamMember requireMember(UUID teamId, UUID userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> teamRepository.existsById(teamId)
                        ? new ForbiddenException("You are not a member of this team")
                        : NotFoundException.of("Team", teamId));
    }

    public TeamMember requireManager(UUID teamId, UUID userId) {
        TeamMember member = requireMember(teamId, userId);
        if (!member.getRole().canManage()) {
            throw new ForbiddenException("Only team owners and admins can perform this action");
        }
        return member;
    }

    public TeamMember requireOwner(UUID teamId, UUID userId) {
        TeamMember member = requireMember(teamId, userId);
        if (member.getRole() != TeamRole.OWNER) {
            throw new ForbiddenException("Only the team owner can perform this action");
        }
        return member;
    }

    public boolean isMember(UUID teamId, UUID userId) {
        return teamMemberRepository.existsByTeamIdAndUserId(teamId, userId);
    }

    public void requireAssigneeIsMember(UUID teamId, User assignee) {
        if (!isMember(teamId, assignee.getId())) {
            throw new ForbiddenException("The assignee must be a member of the team");
        }
    }
}
