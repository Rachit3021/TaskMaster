package com.taskflow.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByToken(String token);

    Optional<Invitation> findByIdAndTeamId(UUID id, UUID teamId);

    boolean existsByTeamIdAndEmailIgnoreCaseAndStatus(UUID teamId, String email, InvitationStatus status);

    List<Invitation> findByTeamIdOrderByCreatedAtDesc(UUID teamId);

    List<Invitation> findByEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(String email, InvitationStatus status);
}
