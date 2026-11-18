package com.taskflow.task;

import com.taskflow.team.TeamMember;
import com.taskflow.task.dto.TaskFilter;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Composable JPA specifications for task filtering. */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> inProject(UUID projectId) {
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Task> assignedTo(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), userId);
    }

    /** Restricts results to teams the user still belongs to. */
    public static Specification<Task> accessibleBy(UUID userId) {
        return (root, query, cb) -> {
            Subquery<UUID> teamIds = query.subquery(UUID.class);
            Root<TeamMember> member = teamIds.from(TeamMember.class);
            teamIds.select(member.get("team").get("id"))
                    .where(cb.equal(member.get("user").get("id"), userId));
            return root.get("project").get("team").get("id").in(teamIds);
        };
    }

    public static Specification<Task> matching(TaskFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }
            if (filter.priorities() != null && !filter.priorities().isEmpty()) {
                predicates.add(root.get("priority").in(filter.priorities()));
            }
            if (filter.assigneeId() != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), filter.assigneeId()));
            }
            if (filter.dueBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), filter.dueBefore()));
            }
            if (filter.dueAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), filter.dueAfter()));
            }
            if (Boolean.TRUE.equals(filter.overdue())) {
                predicates.add(cb.lessThan(root.get("dueDate"), LocalDate.now()));
                predicates.add(cb.notEqual(root.get("status"), TaskStatus.DONE));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + escapeLike(filter.search().trim().toLowerCase(Locale.ROOT)) + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern, '\\');
                Predicate descriptionMatch = cb.like(cb.lower(root.get("description")), pattern, '\\');
                predicates.add(cb.or(titleMatch, descriptionMatch));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
