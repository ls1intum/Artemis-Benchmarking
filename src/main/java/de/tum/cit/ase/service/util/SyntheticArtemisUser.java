package de.tum.cit.ase.service.util;

import static de.tum.cit.ase.service.util.RequestType.*;
import static de.tum.cit.ase.service.util.TimeLogUtil.formatDurationFrom;
import static de.tum.cit.ase.service.util.UMLClassDiagrams.CLASS_MODEL_1;
import static de.tum.cit.ase.service.util.UMLClassDiagrams.CLASS_MODEL_2;
import static java.lang.Thread.sleep;
import static java.time.ZonedDateTime.now;

import com.thedeanda.lorem.LoremIpsum;
import de.tum.cit.ase.artemisModel.*;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;

public class SyntheticArtemisUser {

    private final Logger log = LoggerFactory.getLogger(SyntheticArtemisUser.class);

    private final String username;
    private final String password;
    private final String artemisUrl;
    private List<RequestStat> requestStats = new ArrayList<>();
    private WebClient webClient;
    private String courseIdString;
    private String examIdString;
    private Long studentExamId;
    private StudentExam studentExam;

    public SyntheticArtemisUser(String username, String password, String url) {
        this.username = username;
        this.password = password;
        this.artemisUrl = url;
    }

    public List<RequestStat> login() {
        requestStats = new ArrayList<>();
        WebClient webClient = WebClient
            .builder()
            .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
            .baseUrl(artemisUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

        long start = System.nanoTime();
        var payload = Map.of("username", username, "password", password, "rememberMe", true);
        var response = webClient.post().uri("api/public/authenticate").bodyValue(payload).retrieve().toBodilessEntity().block();
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, AUTHENTICATION, success(response)));

        var cookieHeader = response.getHeaders().get("Set-Cookie").get(0);
        var authToken = AuthToken.fromResponseHeaderString(cookieHeader);
        String cookieHeaderToken = authToken.jwtToken();
        this.webClient =
            WebClient
                .builder()
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .baseUrl(artemisUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Cookie", cookieHeaderToken)
                .build();
        return requestStats;
    }

    public List<RequestStat> performInitialCalls() {
        if (webClient == null) {
            throw new IllegalStateException("User " + username + " is not logged in.");
        }
        requestStats = new ArrayList<>();

        getInfo();
        getSystemNotifications();
        getAccount();
        getNotificationSettings();
        getCourses();

        return requestStats;
    }

    public List<RequestStat> participateInExam(long courseId, long examId) {
        if (webClient == null) {
            throw new IllegalStateException("User " + username + " is not logged in.");
        }
        this.courseIdString = String.valueOf(courseId);
        this.examIdString = String.valueOf(examId);
        requestStats = new ArrayList<>();

        navigateIntoExam();
        startExam();
        handleExercises();
        submitStudentExam();
        loadExamSummary();

        return requestStats;
    }

    public void getInfo() {
        long start = System.nanoTime();
        var response = webClient.get().uri("management/info").retrieve().toBodilessEntity().block();
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, MISC, success(response)));
    }

    public void getSystemNotifications() {
        long start = System.nanoTime();
        var response = webClient.get().uri("api/public/system-notifications/active").retrieve().toBodilessEntity().block();
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, MISC, success(response)));
    }

    public void getAccount() {
        long start = System.nanoTime();
        var response = webClient.get().uri("api/public/account").retrieve().toBodilessEntity().block();
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, MISC, success(response)));
    }

    public void getNotificationSettings() {
        long start = System.nanoTime();
        var response = webClient.get().uri("api/notification-settings").retrieve().toBodilessEntity().block();
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, MISC, success(response)));
    }

    public void getCourses() {
        long start = System.nanoTime();
        var response = webClient.get().uri("api/courses/for-dashboard").retrieve().toBodilessEntity().block();
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, MISC, success(response)));
    }

    public void navigateIntoExam() {
        long start = System.nanoTime();
        StudentExam studentExam = webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", courseIdString, "exams", examIdString, "own-student-exam").build())
            .retrieve()
            .bodyToMono(StudentExam.class)
            .block();
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, GET_STUDENT_EXAM, studentExam != null));
        studentExamId = studentExam.getId();
    }

    public void startExam() {
        long start = System.nanoTime();
        studentExam =
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
                            studentExamId.toString(),
                            "conduction"
                        )
                        .build()
                )
                .retrieve()
                .bodyToMono(StudentExam.class)
                .block();
        long duration = System.nanoTime() - start;
        requestStats.add(new RequestStat(now(), duration, START_STUDENT_EXAM, studentExam != null));
        // startExamRequestStats.add(new RequestStat(now(), duration));
    }

    public void handleExercises() {
        for (var exercise : studentExam.getExercises()) {
            if (exercise instanceof ModelingExercise) {
                solveAndSubmitModelingExercise((ModelingExercise) exercise);
            } else if (exercise instanceof TextExercise) {
                solveAndSubmitTextExercise((TextExercise) exercise);
            } else if (exercise instanceof QuizExercise) {
                solveAndSubmitQuizExercise((QuizExercise) exercise);
            } else if (exercise instanceof ProgrammingExercise) {
                solveAndSubmitProgrammingExercise((ProgrammingExercise) exercise);
            }
        }
    }

    public void solveAndSubmitModelingExercise(ModelingExercise modelingExercise) {
        var modelingSubmission = getModelingSubmission(modelingExercise);
        if (modelingSubmission != null) {
            if (new Random().nextBoolean()) {
                modelingSubmission.setModel(CLASS_MODEL_1);
                modelingSubmission.setExplanationText("The model describes ...");
            } else {
                modelingSubmission.setModel(CLASS_MODEL_2);
                modelingSubmission.setExplanationText("Random explanation text ...");
            }

            long start = System.nanoTime();
            var response = webClient
                .put()
                .uri(uriBuilder ->
                    uriBuilder.pathSegment("api", "exercises", modelingExercise.getId().toString(), "modeling-submissions").build()
                )
                .bodyValue(modelingSubmission)
                .retrieve()
                .toBodilessEntity()
                .block();
            requestStats.add(new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE, success(response)));
        }
    }

    public void solveAndSubmitTextExercise(TextExercise textExercise) {
        var textSubmission = getTextSubmission(textExercise);
        if (textSubmission != null) {
            textSubmission.setText(LoremIpsum.getInstance().getParagraphs(2, 4));
            textSubmission.setLanguage(Language.ENGLISH);

            long start = System.nanoTime();
            var response = webClient
                .put()
                .uri(uriBuilder -> uriBuilder.pathSegment("api", "exercises", textExercise.getId().toString(), "text-submissions").build())
                .bodyValue(textSubmission)
                .retrieve()
                .toBodilessEntity()
                .block();
            requestStats.add(new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE, success(response)));
        }
    }

    public void solveAndSubmitQuizExercise(QuizExercise quizExercise) {
        var quizSubmission = getQuizSubmission(quizExercise);
        // TODO: change something in the quiz submission
        if (quizSubmission != null) {
            long start = System.nanoTime();
            var response = webClient
                .put()
                .uri(uriBuilder ->
                    uriBuilder.pathSegment("api", "exercises", quizExercise.getId().toString(), "submissions", "exam").build()
                )
                .bodyValue(quizSubmission)
                .retrieve()
                .toBodilessEntity()
                .block();
            requestStats.add(new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE, success(response)));
        }
    }

    public void solveAndSubmitProgrammingExercise(ProgrammingExercise programmingExercise) {
        var programmingParticipation = (ProgrammingExerciseStudentParticipation) programmingExercise
            .getStudentParticipations()
            .iterator()
            .next();
        var repositoryCloneUrl = programmingParticipation.getRepositoryUrl();
        try {
            long start = System.nanoTime();
            cloneRepo(repositoryCloneUrl);

            int n = new Random().nextInt(10, 15); // we do a random number of commits and pushes to make some noise
            log.info("Commit and push {}x for {}", n, username);
            for (int j = 0; j < n; j++) {
                sleep(100);
                changeFiles();
                commitAndPushRepo();
            }
            log.debug("    Clone and commit+push done in " + formatDurationFrom(start));
        } catch (Exception e) {
            log.error("Error while handling programming exercise for {{}}: {{}}", username, e.getMessage());
        }
    }

    public void submitStudentExam() {
        long start = System.nanoTime();
        var response = webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "courses", courseIdString, "exams", examIdString, "student-exams", "submit").build()
            )
            .bodyValue(studentExam)
            .retrieve()
            .toBodilessEntity()
            .block();
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, SUBMIT_STUDENT_EXAM, success(response)));
        // submitExamRequestStats.add(new RequestStat(now(), duration));
    }

    public void loadExamSummary() {
        long start = System.nanoTime();
        var response = webClient
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
                        studentExamId.toString(),
                        "summary"
                    )
                    .build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, MISC, success(response)));
    }

    public void prepareExam(long courseId, long examId) {
        this.examIdString = String.valueOf(examId);
        this.courseIdString = String.valueOf(courseId);

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
            } catch (InterruptedException ignored) {}

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

    public Course createCourse() {
        var course = new Course("Temporary Benchmarking Course", "benchmark");
        var responseCourse = webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "admin", "courses").build())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData("course", course))
            .retrieve()
            .bodyToMono(Course.class)
            .block();

        return responseCourse;
    }

    public Exam createExam(Course course) {
        var exam = new Exam();
        exam.setTitle("Temporary Benchmarking Exam");
        exam.setStartDate(ZonedDateTime.now().plusDays(1L));
        exam.setVisibleDate(ZonedDateTime.now());
        exam.setEndDate(ZonedDateTime.now().plusDays(1L).plusHours(2L));
        exam.setNumberOfExercisesInExam(4);
        exam.setExamMaxPoints(5);
        exam.setWorkingTime(2 * 60 * 60);
        exam.setCourse(course);

        var responseExam = webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", course.getId().toString(), "exams").build())
            .bodyValue(exam)
            .retrieve()
            .bodyToMono(Exam.class)
            .block();

        return responseExam;
    }

    public void createExamExercises(long courseId, Exam exam) {
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
        programmingExercise.setTitle("Programming Exercise for Benchmarking");
        programmingExercise.setMaxPoints(1.0);
        programmingExercise.setShortName("progForBenchTemp");
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

    public void registerStudentsForCourseAndExam(long courseId, long examId, int numberOfStudents, String usernameTemplate) {
        for (int i = 1; i <= numberOfStudents; i++) {
            var studentName = usernameTemplate.replace("{i}", String.valueOf(i));
            webClient
                .post()
                .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", String.valueOf(courseId), "students", studentName).build())
                .retrieve()
                .toBodilessEntity()
                .block();
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

    public void deleteCourse(long courseId) {
        webClient
            .delete()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "admin", "courses", String.valueOf(courseId)).build())
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    private static HttpClient createHttpClient() {
        return HttpClient
            .create()
            .doOnConnected(conn ->
                conn.addHandlerFirst(new ReadTimeoutHandler(30, TimeUnit.SECONDS)).addHandlerFirst(new WriteTimeoutHandler(30))
            )
            .responseTimeout(Duration.ofSeconds(30))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30 * 1000)
            .secure(spec ->
                spec
                    .sslContext(SslContextBuilder.forClient())
                    .defaultConfiguration(SslProvider.DefaultConfigurationType.TCP)
                    .handshakeTimeout(Duration.ofSeconds(30))
                    .closeNotifyFlushTimeout(Duration.ofSeconds(30))
                    .closeNotifyReadTimeout(Duration.ofSeconds(30))
            );
    }

    @Nullable
    private static ModelingSubmission getModelingSubmission(ModelingExercise modelingExercise) {
        return getSubmissionOfType(modelingExercise, ModelingSubmission.class);
    }

    @Nullable
    private static ProgrammingSubmission getProgrammingSubmission(ProgrammingExercise programmingExercise) {
        return getSubmissionOfType(programmingExercise, ProgrammingSubmission.class);
    }

    @Nullable
    private static TextSubmission getTextSubmission(TextExercise textExercise) {
        return getSubmissionOfType(textExercise, TextSubmission.class);
    }

    @Nullable
    private static QuizSubmission getQuizSubmission(QuizExercise quizExercise) {
        return getSubmissionOfType(quizExercise, QuizSubmission.class);
    }

    @Nullable
    private static <S extends Submission> S getSubmissionOfType(Exercise exercise, Class<S> submissionType) {
        if (!exercise.getStudentParticipations().isEmpty()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            if (!participation.getSubmissions().isEmpty()) {
                var submission = participation.getSubmissions().iterator().next();
                if (submission.getClass().equals(submissionType)) {
                    return (S) submission;
                }
            }
        }
        return null;
    }

    private void commitAndPushRepo() throws IOException, GitAPIException {
        var localPath = Path.of("repos", username);
        log.debug("Commit and push to " + localPath);
        var git = Git.open(localPath.toFile());
        git.add().addFilepattern("src").call();
        git.commit().setMessage("local test").setAllowEmpty(true).call();
        long start = System.nanoTime();
        git.push().setCredentialsProvider(getCredentialsProvider()).call();
        long duration = System.nanoTime() - start;
        requestStats.add(new RequestStat(now(), duration, PUSH, true));
        git.close();
    }

    private void changeFiles() throws IOException {
        // TODO: produce larger and more realistic commits
        var bubbleSort = Path.of("repos", username, "src", "de", "tum", "in", "ase", "BubbleSort.java");
        log.debug("Change file  " + bubbleSort);
        var newContent =
            """
            package de.tum.in.ase;


            import java.util.*;


            public class BubbleSort {

                /**
                 * BubbleSort
                 *
                 * @param BubbleSort
                 */
                public void performSort(final List<Date> input) {


                    //TODO: implement BubbleSort NOW $$1


                }
            }
            """;
        newContent = newContent.replace("$$1", String.valueOf(new Random().nextInt(100)));
        FileUtils.writeStringToFile(bubbleSort.toFile(), newContent, Charset.defaultCharset());
    }

    private void cloneRepo(String repositoryUrl) throws IOException, GitAPIException {
        log.debug("Clone " + repositoryUrl);
        var localPath = Path.of("repos", username);
        FileUtils.deleteDirectory(localPath.toFile());
        long start = System.nanoTime();
        var git = Git
            .cloneRepository()
            .setURI(repositoryUrl)
            .setDirectory(localPath.toFile())
            .setCredentialsProvider(getCredentialsProvider())
            .call();
        requestStats.add(new RequestStat(now(), System.nanoTime() - start, CLONE, true));
        git.close();
        log.debug("Done " + repositoryUrl);
    }

    private UsernamePasswordCredentialsProvider getCredentialsProvider() {
        return new UsernamePasswordCredentialsProvider(username, password);
    }

    private static boolean success(ResponseEntity response) {
        return response != null && response.getStatusCode().is2xxSuccessful();
    }
}
