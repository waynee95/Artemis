package de.tum.cit.aet.artemis.core.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.config.ArtemisConstants;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Seeds a deliberately small, deterministic data set for a local developer instance.
 *
 * <p>It is only a bean in the {@code dev} profile and is opt-in. The marker course makes
 * repeated application starts a no-op. Programming records are synthetic: they deliberately
 * do not create LocalVC repositories or LocalCI build plans.</p>
 */
@Component
@Profile(ArtemisConstants.SPRING_PROFILE_DEVELOPMENT)
@ConditionalOnProperty(prefix = "artemis.demo-data", name = "enabled", havingValue = "true")
public class DemoDataGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataGenerator.class);

    private static final String PREFIX = "demo-";

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final UserCourseRoleRepository userCourseRoleRepository;
    private final ExerciseRepository exerciseRepository;
    private final StudentParticipationRepository studentParticipationRepository;
    private final SubmissionRepository submissionRepository;
    private final ResultRepository resultRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataGenerator(CourseRepository courseRepository, UserRepository userRepository, AuthorityRepository authorityRepository,
            UserCourseRoleRepository userCourseRoleRepository, ExerciseRepository exerciseRepository, StudentParticipationRepository studentParticipationRepository,
            SubmissionRepository submissionRepository, ResultRepository resultRepository, PasswordEncoder passwordEncoder) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.userCourseRoleRepository = userCourseRoleRepository;
        this.exerciseRepository = exerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!courseRepository.findAllByShortName("DEMOPROG").isEmpty()) {
            log.info("Demo data already exists; skipping generation.");
            return;
        }

        var instructors = createUsers("instructor", 5, Role.INSTRUCTOR);
        var tutors = createUsers("tutor", 8, Role.TEACHING_ASSISTANT);
        var students = createUsers("student", 100, Role.STUDENT);
        List<Course> courses = List.of(createCourse("Introduction to Programming", "DEMOPROG"), createCourse("Software Engineering", "DEMOSE"),
                createCourse("Algorithms & Data Structures", "DEMOADS"));

        for (int courseIndex = 0; courseIndex < courses.size(); courseIndex++) {
            var course = courses.get(courseIndex);
            enroll(course, instructors, CourseRole.INSTRUCTOR);
            enroll(course, tutors.subList(courseIndex * 2, courseIndex * 2 + 2), CourseRole.TEACHING_ASSISTANT);
            var courseStudents = students.subList(courseIndex * 33, courseIndex == 2 ? 100 : courseIndex * 33 + 33);
            enroll(course, courseStudents, CourseRole.STUDENT);
            var exercises = createExercises(course);
            createProgress(courseStudents, tutors, exercises);
        }
        log.info("Created local demo data: 3 courses, 113 users, 24 exercises and persona-based progress.");
    }

    private List<User> createUsers(String role, int count, Role authority) {
        var storedAuthority = authorityRepository.findById(authority.getAuthority()).orElseGet(() -> authorityRepository.save(new Authority(authority.getAuthority())));
        return java.util.stream.IntStream.rangeClosed(1, count).mapToObj(number -> {
            var login = "%s%s-%03d".formatted(PREFIX, role, number);
            var user = new User();
            user.setLogin(login);
            user.setFirstName(role.substring(0, 1).toUpperCase(Locale.ROOT) + role.substring(1));
            user.setLastName("Demo %03d".formatted(number));
            user.setEmail(login + "@example.invalid");
            user.setPassword(passwordEncoder.encode("demo"));
            user.setActivated(true);
            user.setLangKey("en");
            user.setTestUser(true);
            user.setAuthorities(Set.of(storedAuthority));
            return userRepository.save(user);
        }).toList();
    }

    private Course createCourse(String title, String shortName) {
        var now = ZonedDateTime.now();
        var course = new Course();
        course.setTitle(title);
        course.setShortName(shortName);
        course.setDescription("Demo course for exploring Artemis from student, tutor, and instructor perspectives.");
        course.setSemester("Demo Semester");
        course.setLanguage(Language.ENGLISH);
        course.setStartDate(now.minusWeeks(8));
        course.setEndDate(now.plusWeeks(8));
        course.setMaxPoints(200);
        course.setLearningPathsEnabled(true);
        return courseRepository.save(course);
    }

    private void enroll(Course course, List<User> users, CourseRole role) {
        userCourseRoleRepository.saveAll(users.stream().map(user -> new UserCourseRole(user, course, role)).toList());
    }

    private List<Exercise> createExercises(Course course) {
        return java.util.stream.IntStream.range(0, 8).mapToObj(index -> {
            Exercise exercise = switch (index % 5) {
                case 0 -> programmingExercise();
                case 1 -> quizExercise();
                case 2 -> new TextExercise();
                case 3 -> modelingExercise();
                default -> fileUploadExercise();
            };
            var now = ZonedDateTime.now();
            exercise.setCourse(course);
            exercise.setTitle("%s — Demo exercise %02d".formatted(course.getTitle(), index + 1));
            exercise.setShortName("%s-%02d".formatted(course.getShortName().toLowerCase(Locale.ROOT), index + 1));
            exercise.setProblemStatement("A realistic demo task with feedback and a mix of completed and unfinished work.");
            exercise.setMaxPoints(10.0);
            exercise.setBonusPoints(0.0);
            exercise.setMode(ExerciseMode.INDIVIDUAL);
            exercise.setDifficulty(DifficultyLevel.MEDIUM);
            exercise.setReleaseDate(now.minusDays(42 - index * 5L));
            exercise.setDueDate(index < 6 ? now.minusDays(30 - index * 4L) : now.plusDays(index * 4L));
            exercise.setAssessmentDueDate(now.plusDays(14));
            exercise.getCategories().add("Demo");
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                programmingExercise.generateAndSetProjectKey();
            }
            return exerciseRepository.save(exercise);
        }).toList();
    }

    private ProgrammingExercise programmingExercise() {
        var exercise = new ProgrammingExercise();
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setPackageName("de.demo");
        exercise.setAllowOnlineEditor(true);
        exercise.setAllowOfflineIde(true);
        return exercise;
    }

    private QuizExercise quizExercise() {
        var exercise = new QuizExercise();
        exercise.setQuizMode(QuizMode.INDIVIDUAL);
        exercise.setDuration(600);
        return exercise;
    }

    private ModelingExercise modelingExercise() {
        var exercise = new ModelingExercise();
        exercise.setDiagramType(DiagramType.ClassDiagram);
        return exercise;
    }

    private FileUploadExercise fileUploadExercise() {
        var exercise = new FileUploadExercise();
        exercise.setFilePattern(".*\\.(pdf|zip)");
        return exercise;
    }

    private void createProgress(List<User> students, List<User> tutors, List<Exercise> exercises) {
        for (int studentIndex = 0; studentIndex < students.size(); studentIndex++) {
            int persona = studentIndex % 5;
            int completed = switch (persona) {
                case 0 -> 8; // excellent
                case 1 -> 7; // good
                case 2 -> 5; // average
                case 3 -> 3; // struggling
                default -> 0; // inactive
            };
            for (int exerciseIndex = 0; exerciseIndex < completed; exerciseIndex++) {
                var exercise = exercises.get(exerciseIndex);
                double score = scoreFor(persona, exerciseIndex);
                var participation = createParticipation(exercise, students.get(studentIndex));
                createSubmissionAndResult(exercise, participation, score, tutors.get((studentIndex + exerciseIndex) % tutors.size()), exerciseIndex == 1);
                if (exerciseIndex == 2) {
                    createSubmissionAndResult(exercise, participation, Math.max(0, score - 20), tutors.get(studentIndex % tutors.size()), false);
                }
            }
        }
    }

    private double scoreFor(int persona, int exerciseIndex) {
        return switch (persona) {
            case 0 -> 88 + (exerciseIndex % 3) * 6;
            case 1 -> 72 + (exerciseIndex % 4) * 5;
            case 2 -> 48 + (exerciseIndex % 4) * 6;
            default -> 12 + (exerciseIndex % 3) * 12;
        };
    }

    private StudentParticipation createParticipation(Exercise exercise, User student) {
        StudentParticipation participation = exercise instanceof ProgrammingExercise ? new ProgrammingExerciseStudentParticipation("main") : new StudentParticipation();
        participation.setExercise(exercise);
        participation.setParticipant(student);
        participation.setInitializationDate(ZonedDateTime.now().minusDays(20));
        return studentParticipationRepository.save(participation);
    }

    private void createSubmissionAndResult(Exercise exercise, StudentParticipation participation, double score, User tutor, boolean late) {
        Submission submission = submissionFor(exercise);
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmissionDate(ZonedDateTime.now().minusDays(late ? 1 : 10));
        submission = submissionRepository.save(submission);

        var result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(exercise.getId());
        result.setScore(score);
        result.setRated(!late);
        result.setCompletionDate(ZonedDateTime.now().minusDays(late ? 1 : 9));
        result.setAssessor(tutor);
        result.setAssessmentType(exercise instanceof ProgrammingExercise || exercise instanceof QuizExercise ? AssessmentType.AUTOMATIC : AssessmentType.MANUAL);
        resultRepository.save(result);
    }

    private Submission submissionFor(Exercise exercise) {
        return switch (exercise) {
            case ProgrammingExercise ignored -> new ProgrammingSubmission();
            case QuizExercise ignored -> new QuizSubmission();
            case TextExercise ignored -> new TextSubmission().text("A concise demo answer explaining the design decisions.");
            case ModelingExercise ignored -> new ModelingSubmission();
            case FileUploadExercise ignored -> new FileUploadSubmission();
            default -> throw new IllegalArgumentException("Unsupported demo exercise type " + exercise.getType());
        };
    }
}
