package com.taskflow.team;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    @Query("""
            select t from Team t
            where exists (select 1 from TeamMember m where m.team = t and m.user.id = :userId)
            """)
    Page<Team> findForUser(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select t from Team t
            where exists (select 1 from TeamMember m where m.team = t and m.user.id = :userId)
              and (lower(t.name) like lower(concat('%', :query, '%'))
                   or lower(coalesce(t.description, '')) like lower(concat('%', :query, '%')))
            """)
    Page<Team> searchForUser(@Param("userId") UUID userId,
                             @Param("query") String query,
                             Pageable pageable);
}
