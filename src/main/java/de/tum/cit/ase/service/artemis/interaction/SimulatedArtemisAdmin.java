package de.tum.cit.ase.service.artemis.interaction;

import static java.lang.Thread.sleep;
import static java.time.ZonedDateTime.now;

import de.tum.cit.ase.artemisModel.*;
import de.tum.cit.ase.domain.ArtemisUser;
import de.tum.cit.ase.service.artemis.ArtemisUserService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;

public class SimulatedArtemisAdmin extends SimulatedArtemisUser {

    public SimulatedArtemisAdmin(String artemisUrl, ArtemisUser artemisUser, ArtemisUserService artemisUserService) {
        super(artemisUrl, artemisUser, artemisUserService);
        log = LoggerFactory.getLogger(SimulatedArtemisAdmin.class);
    }

    public SimulatedArtemisAdmin(String artemisUrl, String username, String password) {
        super(artemisUrl, username, password);
        log = LoggerFactory.getLogger(SimulatedArtemisAdmin.class);
    }

    @Override
    protected void checkAccess() {
        var response = webClient.get().uri("api/public/account").retrieve().bodyToMono(User.class).block();
        this.authenticated =
            response != null && (response.getAuthorities().contains("ROLE_ADMIN") || response.getAuthorities().contains("ROLE_INSTRUCTOR"));
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

        // Get exam
        Exam exam = webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment("api", "courses", courseIdString, "exams", examIdString)
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

        // Update exam
        webClient
            .put()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", courseIdString, "exams").build())
            .bodyValue(exam)
            .retrieve()
            .toBodilessEntity()
            .block();

        // Generate student exams
        webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "courses", courseIdString, "exams", examIdString, "generate-student-exams").build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();

        // Prepare exercise start
        webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "courses", courseIdString, "exams", examIdString, "student-exams", "start-exercises").build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();

        // Wait for exercise preparation to finish
        ExamExerciseStartPreparationStatus status;
        do {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            status =
                webClient
                    .get()
                    .uri(uriBuilder ->
                        uriBuilder
                            .pathSegment(
                                "api",
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

        // Update exam
        webClient
            .put()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", courseIdString, "exams").build())
            .bodyValue(exam)
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
        var course = new Course("Temporary Benchmarking Course " + randomInt, "benchmark" + randomInt);

        return webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "admin", "courses").build())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData("course", course))
            .retrieve()
            .bodyToMono(Course.class)
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

        var exam = new Exam();
        var randomInt = (int) (Math.random() * 10_0000);
        exam.setTitle("Temporary Benchmarking Exam" + randomInt);
        exam.setStartDate(ZonedDateTime.now().plusDays(1L));
        exam.setVisibleDate(ZonedDateTime.now());
        exam.setEndDate(ZonedDateTime.now().plusDays(1L).plusHours(2L));
        exam.setNumberOfExercisesInExam(4);
        exam.setExamMaxPoints(5);
        exam.setWorkingTime(2 * 60 * 60);
        exam.setCourse(course);

        return webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", course.getId().toString(), "exams").build())
            .bodyValue(exam)
            .retrieve()
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

        var textExerciseGroup = new ExerciseGroup();
        textExerciseGroup.setTitle("Text Exercise Group");
        textExerciseGroup.setMandatory(true);
        textExerciseGroup.setExam(exam);

        textExerciseGroup =
            webClient
                .post()
                .uri(uriBuilder ->
                    uriBuilder
                        .pathSegment("api", "courses", String.valueOf(courseId), "exams", exam.getId().toString(), "exerciseGroups")
                        .build()
                )
                .bodyValue(textExerciseGroup)
                .retrieve()
                .bodyToMono(ExerciseGroup.class)
                .block();

        var textExercise = new TextExercise();
        textExercise.setExerciseGroup(textExerciseGroup);
        textExercise.setTitle("Text Exercise");
        textExercise.setMaxPoints(1.0);

        webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "text-exercises").build())
            .bodyValue(textExercise)
            .retrieve()
            .toBodilessEntity()
            .block();

        var modelingExerciseGroup = new ExerciseGroup();
        modelingExerciseGroup.setTitle("Modeling Exercise Group");
        modelingExerciseGroup.setMandatory(true);
        modelingExerciseGroup.setExam(exam);

        modelingExerciseGroup =
            webClient
                .post()
                .uri(uriBuilder ->
                    uriBuilder
                        .pathSegment("api", "courses", String.valueOf(courseId), "exams", exam.getId().toString(), "exerciseGroups")
                        .build()
                )
                .bodyValue(modelingExerciseGroup)
                .retrieve()
                .bodyToMono(ExerciseGroup.class)
                .block();

        var modelingExercise = new ModelingExercise();
        modelingExercise.setExerciseGroup(modelingExerciseGroup);
        modelingExercise.setTitle("Modeling Exercise");
        modelingExercise.setMaxPoints(1.0);

        webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "modeling-exercises").build())
            .bodyValue(modelingExercise)
            .retrieve()
            .toBodilessEntity()
            .block();

        var programmingExerciseGroup = new ExerciseGroup();
        programmingExerciseGroup.setTitle("Programming Exercise Group");
        programmingExerciseGroup.setMandatory(true);
        programmingExerciseGroup.setExam(exam);

        programmingExerciseGroup =
            webClient
                .post()
                .uri(uriBuilder ->
                    uriBuilder
                        .pathSegment("api", "courses", String.valueOf(courseId), "exams", exam.getId().toString(), "exerciseGroups")
                        .build()
                )
                .bodyValue(programmingExerciseGroup)
                .retrieve()
                .bodyToMono(ExerciseGroup.class)
                .block();

        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setExerciseGroup(programmingExerciseGroup);
        programmingExercise.setTitle("Programming Exercise for " + exam.getTitle());
        programmingExercise.setMaxPoints(1.0);
        programmingExercise.setShortName("progForBenchTemp" + exam.getId());
        programmingExercise.setPackageName("progforbenchtemp");

        webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "programming-exercises", "setup").build())
            .bodyValue(programmingExercise)
            .retrieve()
            .toBodilessEntity()
            .block();

        var quizExerciseGroup = new ExerciseGroup();
        quizExerciseGroup.setTitle("Quiz Exercise Group");
        quizExerciseGroup.setMandatory(true);
        quizExerciseGroup.setExam(exam);

        quizExerciseGroup =
            webClient
                .post()
                .uri(uriBuilder ->
                    uriBuilder
                        .pathSegment("api", "courses", String.valueOf(courseId), "exams", exam.getId().toString(), "exerciseGroups")
                        .build()
                )
                .bodyValue(quizExerciseGroup)
                .retrieve()
                .bodyToMono(ExerciseGroup.class)
                .block();

        var quizExercise = new QuizExercise();
        quizExercise.setExerciseGroup(quizExerciseGroup);
        quizExercise.setTitle("Quiz Exercise");
        var question1 = new MultipleChoiceQuestion();
        question1.setTitle("Question 1");
        question1.setText("What is the answer to life, the universe and everything?");
        question1.setPoints(2.0);
        var answer1 = new AnswerOption();
        answer1.setText("42");
        answer1.setIsCorrect(true);
        question1.getAnswerOptions().add(answer1);
        var answer2 = new AnswerOption();
        answer2.setText("12");
        answer2.setIsCorrect(false);
        question1.getAnswerOptions().add(answer2);
        quizExercise.getQuizQuestions().add(question1);

        webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "quiz-exercises").build())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData("exercise", quizExercise))
            .retrieve()
            .toBodilessEntity()
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
            Flowable
                .range(0, students.length)
                .parallel(threadCount)
                .runOn(scheduler)
                .doOnNext(i -> {
                    try {
                        webClient
                            .post()
                            .uri(uriBuilder ->
                                uriBuilder.pathSegment("api", "courses", String.valueOf(courseId), "students", students[i].username).build()
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
                    .pathSegment("api", "courses", String.valueOf(courseId), "exams", String.valueOf(examId), "register-course-students")
                    .build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public Course getCourse(long courseId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }
        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", String.valueOf(courseId)).build())
            .retrieve()
            .bodyToMono(Course.class)
            .block();
    }

    public void deleteCourse(long courseId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }

        webClient
            .delete()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "admin", "courses", String.valueOf(courseId)).build())
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public void deleteExam(long courseId, long examId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or does not have the necessary access rights.");
        }

        webClient
            .delete()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", String.valueOf(courseId), "exams", String.valueOf(examId)).build())
            .retrieve()
            .toBodilessEntity()
            .block();
    }
}