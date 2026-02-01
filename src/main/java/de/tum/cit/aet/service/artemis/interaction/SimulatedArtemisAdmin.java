package de.tum.cit.aet.service.artemis.interaction;

import static java.lang.Thread.sleep;
import static java.time.ZonedDateTime.now;

import de.tum.cit.aet.artemisModel.*;
import de.tum.cit.aet.domain.ArtemisUser;
import de.tum.cit.aet.service.artemis.ArtemisUserService;
import de.tum.cit.aet.service.artemis.util.ArtemisUserDTO;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * A simulated Artemis admin or instructor user that can be used to interact with the Artemis server.
 */
public class SimulatedArtemisAdmin extends SimulatedArtemisUser {

    public SimulatedArtemisAdmin(String artemisUrl, ArtemisUser artemisUser, ArtemisUserService artemisUserService) {
        super(artemisUrl, artemisUser, artemisUserService);
        log = LoggerFactory.getLogger(SimulatedArtemisAdmin.class);
    }

    public SimulatedArtemisAdmin(String artemisUrl, String username, String password) {
        super(artemisUrl, username, password);
        log = LoggerFactory.getLogger(SimulatedArtemisAdmin.class);
    }

    SimulatedArtemisAdmin(String artemisUrl, String username, String password, Supplier<WebClient.Builder> webClientBuilderSupplier) {
        super(artemisUrl, username, password, webClientBuilderSupplier);
        log = LoggerFactory.getLogger(SimulatedArtemisAdmin.class);
    }

    @Override
    protected void checkAccess() {
        var response = webClient.get().uri("api/core/public/account").retrieve().bodyToMono(User.class).block();
        if (response == null) {
            this.authenticated = false;
            return;
        }

        var authorities = response.getAuthorities();
        if (authorities == null || authorities.isEmpty()) {
            log.warn(
                "Account response for user {} contains no authorities. Assuming authenticated; server will enforce access rights.",
                username
            );
            this.authenticated = true;
            return;
        }

        this.authenticated =
            authorities.contains("ROLE_ADMIN") ||
            authorities.contains("ROLE_INSTRUCTOR") ||
            authorities.contains("ROLE_SUPER_ADMIN") ||
            authorities.contains("ADMIN") ||
            authorities.contains("INSTRUCTOR") ||
            authorities.contains("SUPER_ADMIN");
        if (!this.authenticated) {
            log.warn("User {} does not have admin/instructor roles. Authorities: {}", username, authorities);
        }
    }

    /**
     * Prepare an exam for benchmarking, i.e. generate student exams, prepare exercise start, wait for preparation to finish and set start-date to now.
     * @param courseId the ID of the course
     * @param examId the ID of the exam
     */
    public void prepareExam(long courseId, long examId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }
        var examIdString = String.valueOf(examId);
        var courseIdString = String.valueOf(courseId);

        log.debug("Fetching exam...");
        // Get exam
        Exam exam = webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment("api", "exam", "courses", courseIdString, "exams", examIdString)
                    .query("withStudents=false&withExerciseGroups=false")
                    .build()
            )
            .retrieve()
            .bodyToMono(Exam.class)
            .block();
        if (exam == null) {
            log.error("Exam could not be fetched!");
            return;
        }

        // Set start and end date to future
        exam.setVisibleDate(ZonedDateTime.now());
        exam.setStartDate(ZonedDateTime.now().plusDays(1L));
        exam.setEndDate(ZonedDateTime.now().plusDays(5L));

        log.info("Updating exam...");
        // Update exam
        webClient
            .put()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exam", "courses", courseIdString, "exams").build())
            .bodyValue(ExamUpdateDTO.fromExam(exam))
            .retrieve()
            .toBodilessEntity()
            .block();

        log.info("Generating student exams...");
        // Generate student exams
        webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "exam", "courses", courseIdString, "exams", examIdString, "generate-student-exams").build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();

        log.info("Preparing exercise start...");
        // Prepare exercise start
        webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment("api", "exam", "courses", courseIdString, "exams", examIdString, "student-exams", "start-exercises")
                    .build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();

        // Wait for exercise preparation to finish
        log.info("Waiting for exercise preparation to finish...");
        ExamExerciseStartPreparationStatus status;
        do {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            status = webClient
                .get()
                .uri(uriBuilder ->
                    uriBuilder
                        .pathSegment(
                            "api",
                            "exam",
                            "courses",
                            courseIdString,
                            "exams",
                            examIdString,
                            "student-exams",
                            "start-exercises",
                            "status"
                        )
                        .build()
                )
                .retrieve()
                .bodyToMono(ExamExerciseStartPreparationStatus.class)
                .block();
            if (status == null) {
                log.warn("Preparation status undefined");
            } else {
                log.info(
                    "Preparation complete for {{}}, failed for {{}}, overall {{}}",
                    status.finished(),
                    status.failed(),
                    status.overall()
                );
            }
        } while (status != null && status.finished() + status.failed() < status.overall());
        if (status != null && status.failed() > 0) {
            log.warn("Preparation failed for {{}} students", status.failed());
        }

        // Set start-date to now
        exam.setStartDate(now());

        log.info("Updating exam...");
        // Update exam
        webClient
            .put()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exam", "courses", courseIdString, "exams").build())
            .bodyValue(ExamUpdateDTO.fromExam(exam))
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    /**
     * Create a course for benchmarking.
     * @return the created course
     */
    public Course createCourse() {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }

        var randomInt = (int) (Math.random() * 10_0000);
        var courseDto = CourseCreateDTO.forBenchmarking("Temporary Benchmarking Course " + randomInt, "benchmark" + randomInt);

        return webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "core", "admin", "courses").build())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData("course", courseDto))
            .retrieve()
            .onStatus(HttpStatusCode::isError,
                response ->
                    response.bodyToMono(String.class).defaultIfEmpty("").flatMap(body -> {
                        log.error("Course creation failed with status {}. Response body: {}", response.statusCode(), body);
                        return Mono.error(
                            new IllegalStateException("Course creation failed with status " + response.statusCode() + ": " + body)
                        );
                    })
            )
            .bodyToMono(Course.class)
            .block();
    }

    /**
     * Cancel all queued build jobs on Artemis.
     * <p>
     * Note: This method is only available for admins.
     */
    public void cancelAllQueuedBuildJobs() {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }

        webClient
            .delete()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "core", "admin", "cancel-all-queued-jobs").build())
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    /**
     * Cancel all running build jobs on Artemis.
     * <p>
     * Note: This method is only available for admins.
     */
    public void cancelAllRunningBuildJobs() {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }

        webClient
            .delete()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "core", "admin", "cancel-all-running-jobs").build())
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    /**
     * Create an exam for benchmarking.
     * @param course the course for which to create the exam
     * @return the created exam
     */
    public Exam createExam(Course course) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }

        var randomInt = (int) (Math.random() * 10_0000);
        var examDto = ExamCreateDTO.forBenchmarking(
            "Temporary Benchmarking Exam" + randomInt,
            course.getId(),
            ZonedDateTime.now(),
            ZonedDateTime.now().plusDays(1L),
            ZonedDateTime.now().plusDays(1L).plusHours(2L)
        );

        return webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exam", "courses", course.getId().toString(), "exams").build())
            .bodyValue(examDto)
            .retrieve()
            .onStatus(HttpStatusCode::isError,
                response ->
                    response.bodyToMono(String.class).defaultIfEmpty("").flatMap(body -> {
                        log.error("Exam creation failed with status {}. Response body: {}", response.statusCode(), body);
                        return Mono.error(
                            new IllegalStateException("Exam creation failed with status " + response.statusCode() + ": " + body)
                        );
                    })
            )
            .bodyToMono(Exam.class)
            .block();
    }

    /**
     * Create exam exercises for benchmarking, i.e. one text, one modeling, one programming and one quiz exercise.
     * @param courseId the ID of the course to which the exam belongs
     * @param exam the exam for which to create the exercises
     */
    public void createExamExercises(long courseId, Exam exam) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }
        var textExerciseGroup = postExerciseGroup(
            courseId,
            exam,
            ExerciseGroupCreateDTO.forBenchmarking("Text Exercise Group", exam.getId())
        );
        var textExercise = TextExerciseCreateDTO.forBenchmarking("Text Exercise", textExerciseGroup.getId());

        webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "text", "text-exercises").build())
            .bodyValue(textExercise)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> logRequestError("Text exercise creation", response))
            .toBodilessEntity()
            .block();

        var modelingExerciseGroup = postExerciseGroup(
            courseId,
            exam,
            ExerciseGroupCreateDTO.forBenchmarking("Modeling Exercise Group", exam.getId())
        );
        var modelingExercise = ModelingExerciseCreateDTO.forBenchmarking("Modeling Exercise", modelingExerciseGroup.getId());

        webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "modeling", "modeling-exercises").build())
            .bodyValue(modelingExercise)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> logRequestError("Modeling exercise creation", response))
            .toBodilessEntity()
            .block();

        var programmingExerciseGroup = postExerciseGroup(
            courseId,
            exam,
            ExerciseGroupCreateDTO.forBenchmarking("Programming Exercise Group", exam.getId())
        );
        var programmingExercise = ProgrammingExerciseCreateDTO.forExamBenchmarking(
            "Programming Exercise for " + exam.getTitle(),
            programmingExerciseGroup.getId(),
            "progForBenchTemp" + exam.getId(),
            "progforbenchtemp"
        );

        webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "programming", "programming-exercises", "setup").build())
            .bodyValue(programmingExercise)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> logRequestError("Programming exercise creation", response))
            .toBodilessEntity()
            .block();

        var quizExerciseGroup = postExerciseGroup(
            courseId,
            exam,
            ExerciseGroupCreateDTO.forBenchmarking("Quiz Exercise Group", exam.getId())
        );
        var quizExercise = QuizExerciseCreateDTO.forBenchmarking(
            "Quiz Exercise",
            List.of(
                MultipleChoiceQuestionCreateDTO.forBenchmarking(
                    "Question 1",
                    "What is the answer to life, the universe and everything?",
                    2.0,
                    List.of(
                        AnswerOptionCreateDTO.correct("42"),
                        AnswerOptionCreateDTO.incorrect("12")
                    )
                )
            )
        );

        webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "quiz", "exercise-groups", String.valueOf(quizExerciseGroup.getId()), "quiz-exercises").build()
            )
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData("exercise", quizExercise))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> logRequestError("Quiz exercise creation", response))
            .toBodilessEntity()
            .block();

        var fileUploadExerciseGroup = postExerciseGroup(
            courseId,
            exam,
            ExerciseGroupCreateDTO.forBenchmarking("File Upload Exercise Group", exam.getId())
        );
        var fileUploadExercise = FileUploadExerciseCreateDTO.forBenchmarking(
            "File Upload Exercise",
            fileUploadExerciseGroup.getId(),
            "pdf,txt"
        );
        webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "fileupload", "file-upload-exercises").build())
            .bodyValue(fileUploadExercise)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> logRequestError("File upload exercise creation", response))
            .toBodilessEntity()
            .block();
    }

    /**
     * Post an exercise group to the exam.
     * @param exam the exam to which the exercise group belongs
     * @param exerciseGroup the exercise group to post
     * @return the created exercise group
     */
    private ExerciseGroup postExerciseGroup(long courseId, Exam exam, ExerciseGroupCreateDTO exerciseGroup) {
        return webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment(
                        "api",
                        "exam",
                        "courses",
                        String.valueOf(courseId),
                        "exams",
                        exam.getId().toString(),
                        "exercise-groups"
                    )
                    .build()
            )
            .bodyValue(exerciseGroup)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> logRequestError("Exercise group creation", response))
            .bodyToMono(ExerciseGroup.class)
            .block();
    }

    /**
     * Create a course programming exercise for benchmarking.
     * @param course the course for which to create the exercise
     * @return the created exercise
     */
    public ProgrammingExercise createCourseProgrammingExercise(Course course) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }

        var randomInt = (int) (Math.random() * 1000);
        var programmingExercise = ProgrammingExerciseCreateDTO.forCourseBenchmarking(
            "Temporary Benchmarking Programming Exercise " + course.getId() + "-" + randomInt,
            course.getId(),
            "course" + course.getId() + "prog" + randomInt,
            "progForBenchTemp"
        );

        return webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "programming", "programming-exercises", "setup").build())
            .bodyValue(programmingExercise)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> logRequestError("Course programming exercise creation", response))
            .bodyToMono(ProgrammingExercise.class)
            .block();
    }

    /**
     * Register the given students for the course. The registration is parallelized to speed up the process.
     * @param courseId the ID of the course
     * @param students the students to register
     */
    public void registerStudentsForCourse(long courseId, SimulatedArtemisStudent[] students) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }

        int threadCount = Integer.min(Runtime.getRuntime().availableProcessors() * 10, students.length);
        ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(threadCount);
        Scheduler scheduler = Schedulers.from(threadPoolExecutor);

        try {
            Flowable.range(0, students.length)
                .parallel(threadCount)
                .runOn(scheduler)
                .doOnNext(i -> {
                    try {
                        webClient
                            .post()
                            .uri(uriBuilder ->
                                uriBuilder
                                    .pathSegment("api", "core", "courses", String.valueOf(courseId), "students", students[i].username)
                                    .build()
                            )
                            .retrieve()
                            .toBodilessEntity()
                            .block();
                    } catch (Exception e) {
                        log.warn("Could not register student {{}} for course: {{}}", students[i].username, e.getMessage());
                    }
                })
                .sequential()
                .blockingSubscribe();
        } finally {
            threadPoolExecutor.shutdownNow();
            scheduler.shutdown();
        }
    }

    /**
     * Register all students of the given course for the given exam.
     * @param courseId the ID of the course
     * @param examId the ID of the exam
     */
    public void registerStudentsForExam(long courseId, long examId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }

        webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment(
                        "api",
                        "exam",
                        "courses",
                        String.valueOf(courseId),
                        "exams",
                        String.valueOf(examId),
                        "register-course-students"
                    )
                    .build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    /**
     * Get the course with the given ID.
     * @param courseId the ID of the course
     * @return the course with the given ID
     */
    public Course getCourse(long courseId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "core", "courses", String.valueOf(courseId)).build())
            .retrieve()
            .bodyToMono(Course.class)
            .block();
    }

    /**
     * Delete the course with the given ID.
     * @param courseId the ID of the course
     */
    public void deleteCourse(long courseId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }

        webClient
            .delete()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "core", "admin", "courses", String.valueOf(courseId)).build())
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    /**
     * Delete the exam with the given ID.
     * @param courseId the ID of the course to which the exam belongs
     * @param examId the ID of the exam
     */
    public void deleteExam(long courseId, long examId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }

        webClient
            .delete()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "exam", "courses", String.valueOf(courseId), "exams", String.valueOf(examId)).build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    /**
     * Create a new student on Artemis.
     *
     * @param username the username of the student
     * @param password the password of the student
     * @param firstName the first name of the student
     * @param lastName the last name of the student
     * @param email the email of the student
     */
    public void createUser(String username, String password, String firstName, String lastName, String email) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }
        var user = ArtemisUserDTO.forCreation(username, password, firstName, lastName, email);
        webClient.post().uri("api/core/admin/users").bodyValue(user).retrieve().toBodilessEntity().block();
    }

    /**
     * Get the build queue for the given course.
     * <p>
     * Note: We do not get the actual build jobs, but only DomainObjects representing them since we only care about the number of jobs.
     * @param courseId the ID of the course
     * @return the build queue for the given course as a list of DomainObjects
     */
    public List<DomainObject> getBuildQueue(long courseId) {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "programming", "courses", String.valueOf(courseId), "queued-jobs").build())
            .retrieve()
            .bodyToFlux(DomainObject.class)
            .collectList()
            .block();
    }

    /**
     * Get the participations for the given exercise.
     *
     * @param exerciseId the ID of the exercise
     * @return the participations for the given exercise
     */
    public List<Participation> getParticipations(long exerciseId) {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exercise", "exercises", String.valueOf(exerciseId), "participations").build())
            .retrieve()
            .bodyToFlux(Participation.class)
            .collectList()
            .block();
    }

    /**
     * Get the submissions for the given participation.
     *
     * @param participationId the ID of the participation
     * @return the submissions for the given participation
     */
    public List<Submission> getSubmissions(long participationId) {
        return webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "exercise", "participations", String.valueOf(participationId), "submissions").build()
            )
            .retrieve()
            .bodyToFlux(Submission.class)
            .collectList()
            .block();
    }

    /**
     * Get the exam with exercises for the given exam ID.
     * @param examId the ID of the exam
     * @return the exam with exercises for the given exam ID
     */
    public Exam getExamWithExercises(long examId) {
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exam", "exams", String.valueOf(examId)).build())
            .retrieve()
            .bodyToMono(Exam.class)
            .block();
    }

    private Mono<? extends Throwable> logRequestError(String action, ClientResponse response) {
        return response.bodyToMono(String.class).defaultIfEmpty("").flatMap(body -> {
            log.error("{} failed with status {}. Response body: {}", action, response.statusCode(), body);
            return Mono.error(new IllegalStateException(action + " failed with status " + response.statusCode() + ": " + body));
        });
    }
}
