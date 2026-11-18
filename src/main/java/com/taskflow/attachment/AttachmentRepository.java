package com.taskflow.attachment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    Optional<Attachment> findByIdAndTaskId(UUID id, UUID taskId);

    long countByTaskId(UUID taskId);

    @Query("select a.storedName from Attachment a where a.task.project.team.id = :teamId")
    List<String> findStoredNamesByTeamId(@Param("teamId") UUID teamId);

    @Query("select a.storedName from Attachment a where a.task.project.id = :projectId")
    List<String> findStoredNamesByProjectId(@Param("projectId") UUID projectId);
}
