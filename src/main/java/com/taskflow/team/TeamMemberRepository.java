package com.taskflow.team;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);

    @EntityGraph(attributePaths = "user")
    List<TeamMember> findByTeamIdOrderByJoinedAtAsc(UUID teamId);

    long countByTeamId(UUID teamId);

    long countByTeamIdAndRole(UUID teamId, TeamRole role);
}
