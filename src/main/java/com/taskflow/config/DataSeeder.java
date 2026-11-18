package com.taskflow.config;

import com.taskflow.comment.Comment;
import com.taskflow.comment.CommentRepository;
import com.taskflow.project.Project;
import com.taskflow.project.ProjectRepository;
import com.taskflow.task.Task;
import com.taskflow.task.TaskPriority;
import com.taskflow.task.TaskRepository;
import com.taskflow.task.TaskStatus;
import com.taskflow.team.Team;
import com.taskflow.team.TeamMember;
import com.taskflow.team.TeamMemberRepository;
import com.taskflow.team.TeamRepository;
import com.taskflow.team.TeamRole;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Seeds a demo workspace the first time the application starts with
 * {@code app.seed.enabled=true}. Safe to run repeatedly: it exits early when
 * users already exist.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEMO_PASSWORD = "Demo@12345";

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final CommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      TeamRepository teamRepository,
                      TeamMemberRepository teamMemberRepository,
                      ProjectRepository projectRepository,
                      TaskRepository taskRepository,
                      CommentRepository commentRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.commentRepository = commentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Seed skipped: users already exist");
            return;
        }

        User demo = userRepository.save(new User("Demo User", "demo@taskflow.dev",
                passwordEncoder.encode(DEMO_PASSWORD)));
        User maya = userRepository.save(new User("Maya Sharma", "maya@taskflow.dev",
                passwordEncoder.encode(DEMO_PASSWORD)));
        User arjun = userRepository.save(new User("Arjun Patel", "arjun@taskflow.dev",
                passwordEncoder.encode(DEMO_PASSWORD)));

        Team team = teamRepository.save(new Team("TaskFlow Product",
                "Cross-functional team building the TaskFlow web and mobile apps.", "TASKFLOW01", demo));
        teamMemberRepository.save(new TeamMember(team, demo, TeamRole.OWNER));
        teamMemberRepository.save(new TeamMember(team, maya, TeamRole.ADMIN));
        teamMemberRepository.save(new TeamMember(team, arjun, TeamRole.MEMBER));

        Project web = projectRepository.save(new Project(team, "TaskFlow Web App",
                "Customer-facing web application for task tracking.", demo));
        Project mobile = projectRepository.save(new Project(team, "TaskFlow Mobile",
                "Companion mobile app.", maya));

        Task design = task(web, "Design authentication flow", demo);
        design.setDescription("Document the register, login, refresh and logout flows, including "
                + "error states and token storage decisions.");
        design.setStatus(TaskStatus.IN_PROGRESS);
        design.setPriority(TaskPriority.HIGH);
        design.setDueDate(LocalDate.now().plusDays(3));
        design.setAssignee(maya);
        design.setLabels(Set.of("design", "auth"));
        taskRepository.save(design);

        Task filtering = task(web, "Implement task filtering API", demo);
        filtering.setDescription("Support filtering by status, priority, assignee, due date and free "
                + "text search, with whitelisted sorting.");
        filtering.setStatus(TaskStatus.TODO);
        filtering.setPriority(TaskPriority.MEDIUM);
        filtering.setDueDate(LocalDate.now().plusDays(7));
        filtering.setAssignee(demo);
        filtering.setLabels(Set.of("backend", "api"));
        taskRepository.save(filtering);

        Task uploads = task(web, "Add attachment uploads", maya);
        uploads.setDescription("Allow team members to upload and download task attachments with "
                + "content-type validation.");
        uploads.setStatus(TaskStatus.IN_REVIEW);
        uploads.setPriority(TaskPriority.URGENT);
        uploads.setDueDate(LocalDate.now().plusDays(1));
        uploads.setAssignee(maya);
        uploads.setLabels(Set.of("backend", "files"));
        taskRepository.save(uploads);

        Task docs = task(web, "Write the API documentation", demo);
        docs.setDescription("Cover every endpoint with request and response examples.");
        docs.setStatus(TaskStatus.BACKLOG);
        docs.setPriority(TaskPriority.LOW);
        docs.setLabels(Set.of("docs"));
        taskRepository.save(docs);

        Task ci = task(web, "Set up the CI pipeline", demo);
        ci.setDescription("Run tests and build the container image on every push.");
        ci.setStatus(TaskStatus.DONE);
        ci.setPriority(TaskPriority.HIGH);
        ci.setAssignee(arjun);
        ci.setCompletedAt(Instant.now());
        ci.setLabels(Set.of("devops"));
        taskRepository.save(ci);

        Task onboarding = task(mobile, "Create onboarding screens", maya);
        onboarding.setDescription("Three-step onboarding with team invite guidance.");
        onboarding.setStatus(TaskStatus.TODO);
        onboarding.setPriority(TaskPriority.MEDIUM);
        onboarding.setDueDate(LocalDate.now().plusDays(10));
        onboarding.setAssignee(arjun);
        taskRepository.save(onboarding);

        commentRepository.save(new Comment(design, demo,
                "I pushed the first draft to Figma. Please review the refresh-token rotation edge cases."));
        commentRepository.save(new Comment(design, maya,
                "Looks good. I'll add notes on what happens when a refresh token is reused."));

        log.info("Demo data seeded. Login with demo@taskflow.dev / {}", DEMO_PASSWORD);
    }

    private static Task task(Project project, String title, User creator) {
        return new Task(project, title, creator);
    }
}
