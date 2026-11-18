package com.taskflow.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Searches only users that share at least one team with the caller, so the
     * endpoint cannot be used to enumerate the whole user base.
     */
    @Query("""
            select distinct u from User u
            where u.id <> :userId
              and exists (
                select 1 from TeamMember me
                join TeamMember other on other.team = me.team
                where me.user.id = :userId and other.user = u
              )
              and (lower(u.name) like lower(concat('%', :query, '%'))
                   or lower(u.email) like lower(concat('%', :query, '%')))
            """)
    Page<User> searchTeamMates(@Param("userId") UUID userId, @Param("query") String query, Pageable pageable);
}
