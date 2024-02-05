package de.tum.cit.ase.service.artemis.interaction;

import static de.tum.cit.ase.domain.RequestType.*;
import static de.tum.cit.ase.util.TimeLogUtil.formatDurationFrom;
import static java.lang.Thread.sleep;
import static java.time.ZonedDateTime.now;

import com.thedeanda.lorem.LoremIpsum;
import de.tum.cit.ase.artemisModel.*;
import de.tum.cit.ase.domain.ArtemisUser;
import de.tum.cit.ase.domain.RequestStat;
import de.tum.cit.ase.service.artemis.ArtemisUserService;
import de.tum.cit.ase.util.UMLClassDiagrams;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.LoggerFactory;

public class SimulatedArtemisStudent extends SimulatedArtemisUser {

    private static final int MAX_RETRIES = 4; // Maximum number of retries for clone
    private static final long RETRY_DELAY_MS = 5000; // Delay between clone retries in milliseconds

    private String courseIdString;
    private String examIdString;
    private Long studentExamId;
    private StudentExam studentExam;
    private final int numberOfCommitsAndPushesFrom;
    private final int numberOfCommitsAndPushesTo;

    public SimulatedArtemisStudent(
        String artemisUrl,
        ArtemisUser artemisUser,
        ArtemisUserService artemisUserService,
        int numberOfCommitsAndPushesFrom,
        int numberOfCommitsAndPushesTo
    ) {
        super(artemisUrl, artemisUser, artemisUserService);
        log = LoggerFactory.getLogger(SimulatedArtemisStudent.class.getName() + "." + username);
        this.numberOfCommitsAndPushesFrom = numberOfCommitsAndPushesFrom;
        this.numberOfCommitsAndPushesTo = numberOfCommitsAndPushesTo;
    }

    @Override
    protected void checkAccess() {
        var response = webClient.get().uri("api/public/account").retrieve().bodyToMono(User.class).block();
        this.authenticated = response != null && response.getAuthorities().contains("ROLE_USER");
    }

    /**
     * Perform miscellaneous calls to Artemis, e.g. to get the user info, system notifications, account, notification settings, and courses.
     * @return the list of request stats
     */
    public List<RequestStat> performInitialCalls() {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }

        return List.of(getInfo(), getSystemNotifications(), getAccount(), getNotificationSettings(), getCourses());
    }

    /**
     * Participate in an exam, i.e. solve and submit the exercises and fetch live events.
     * @param courseId the ID of the course
     * @param examId the ID of the exam
     * @return the list of request stats
     */
    public List<RequestStat> participateInExam(long courseId, long examId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }
        this.courseIdString = String.valueOf(courseId);
        this.examIdString = String.valueOf(examId);

        List<RequestStat> requestStats = new ArrayList<>();

        requestStats.add(fetchLiveEvents());
        requestStats.addAll(handleExercises());

        return requestStats;
    }

    /**
     * Start participating in an exam, i.e. navigate into the exam and start the exam.
     * @param courseId the ID of the course
     * @param examId the ID of the exam
     * @return the list of request stats
     */
    public List<RequestStat> startExamParticipation(long courseId, long examId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }
        this.courseIdString = String.valueOf(courseId);
        this.examIdString = String.valueOf(examId);

        List<RequestStat> requestStats = new ArrayList<>();

        requestStats.add(navigateIntoExam());
        requestStats.add(startExam());

        return requestStats;
    }

    /**
     * Submit and end an exam, i.e. submit the student exam and load the exam summary.
     * @param courseId the ID of the course
     * @param examId the ID of the exam
     * @return the list of request stats
     */
    public List<RequestStat> submitAndEndExam(long courseId, long examId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }
        this.courseIdString = String.valueOf(courseId);
        this.examIdString = String.valueOf(examId);

        List<RequestStat> requestStats = new ArrayList<>();

        requestStats.add(submitStudentExam());
        requestStats.add(loadExamSummary());

        return requestStats;
    }

    private RequestStat getInfo() {
        long start = System.nanoTime();
        webClient.get().uri("management/info").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getSystemNotifications() {
        long start = System.nanoTime();
        webClient.get().uri("api/public/system-notifications/active").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getAccount() {
        long start = System.nanoTime();
        webClient.get().uri("api/public/account").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getNotificationSettings() {
        long start = System.nanoTime();
        webClient.get().uri("api/notification-settings").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getCourses() {
        long start = System.nanoTime();
        webClient.get().uri("api/courses/for-dashboard").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat navigateIntoExam() {
        long start = System.nanoTime();
        StudentExam studentExam = webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", courseIdString, "exams", examIdString, "own-student-exam").build())
            .retrieve()
            .bodyToMono(StudentExam.class)
            .block();
        var duration = System.nanoTime() - start;

        if (studentExam != null) {
            studentExamId = studentExam.getId();
        }
        return new RequestStat(now(), duration, GET_STUDENT_EXAM);
    }

    private RequestStat startExam() {
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
        return new RequestStat(now(), System.nanoTime() - start, START_STUDENT_EXAM);
    }

    private RequestStat fetchLiveEvents() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "courses", courseIdString, "exams", examIdString, "student-exams", "live-events").build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private List<RequestStat> handleExercises() {
        List<RequestStat> requestStats = new ArrayList<>();
        for (var exercise : studentExam.getExercises()) {
            if (exercise instanceof ModelingExercise) {
                requestStats.add(solveAndSubmitModelingExercise((ModelingExercise) exercise));
            } else if (exercise instanceof TextExercise) {
                requestStats.add(solveAndSubmitTextExercise((TextExercise) exercise));
            } else if (exercise instanceof QuizExercise) {
                requestStats.add(solveAndSubmitQuizExercise((QuizExercise) exercise));
            } else if (exercise instanceof ProgrammingExercise) {
                requestStats.addAll(solveAndSubmitProgrammingExercise((ProgrammingExercise) exercise));
            }
        }
        return requestStats;
    }

    private RequestStat solveAndSubmitModelingExercise(ModelingExercise modelingExercise) {
        var modelingSubmission = getModelingSubmission(modelingExercise);
        if (modelingSubmission != null) {
            if (new Random().nextBoolean()) {
                modelingSubmission.setModel(UMLClassDiagrams.CLASS_MODEL_1);
                modelingSubmission.setExplanationText("The model describes ...");
            } else {
                modelingSubmission.setModel(UMLClassDiagrams.CLASS_MODEL_2);
                modelingSubmission.setExplanationText("Random explanation text ...");
            }

            long start = System.nanoTime();
            webClient
                .put()
                .uri(uriBuilder ->
                    uriBuilder.pathSegment("api", "exercises", modelingExercise.getId().toString(), "modeling-submissions").build()
                )
                .bodyValue(modelingSubmission)
                .retrieve()
                .toBodilessEntity()
                .block();
            return new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE);
        }
        return null;
    }

    private RequestStat solveAndSubmitTextExercise(TextExercise textExercise) {
        var textSubmission = getTextSubmission(textExercise);
        if (textSubmission != null) {
            textSubmission.setText(LoremIpsum.getInstance().getParagraphs(2, 4));
            textSubmission.setLanguage(Language.ENGLISH);

            long start = System.nanoTime();
            webClient
                .put()
                .uri(uriBuilder -> uriBuilder.pathSegment("api", "exercises", textExercise.getId().toString(), "text-submissions").build())
                .bodyValue(textSubmission)
                .retrieve()
                .toBodilessEntity()
                .block();
            return new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE);
        }
        return null;
    }

    private RequestStat solveAndSubmitQuizExercise(QuizExercise quizExercise) {
        var quizSubmission = getQuizSubmission(quizExercise);
        // TODO: change something in the quiz submission
        if (quizSubmission != null) {
            long start = System.nanoTime();
            webClient
                .put()
                .uri(uriBuilder ->
                    uriBuilder.pathSegment("api", "exercises", quizExercise.getId().toString(), "submissions", "exam").build()
                )
                .bodyValue(quizSubmission)
                .retrieve()
                .toBodilessEntity()
                .block();
            return new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE);
        }
        return null;
    }

    private List<RequestStat> solveAndSubmitProgrammingExercise(ProgrammingExercise programmingExercise) {
        var programmingParticipation = (ProgrammingExerciseStudentParticipation) programmingExercise
            .getStudentParticipations()
            .iterator()
            .next();
        var repositoryCloneUrl = programmingParticipation.getRepositoryUri();
        List<RequestStat> requestStats = new ArrayList<>();

        try {
            long start = System.nanoTime();
            requestStats.add(cloneRepo(repositoryCloneUrl));

            int n = new Random().nextInt(numberOfCommitsAndPushesFrom, numberOfCommitsAndPushesTo); // we do a random number of commits and pushes to make some noise
            log.info("Commit and push {}x for {}", n, username);
            for (int j = 0; j < n; j++) {
                sleep(100);
                changeFiles();
                requestStats.add(commitAndPushRepo());
            }
            log.debug("    Clone and commit+push done in " + formatDurationFrom(start));
        } catch (Exception e) {
            log.error("Error while handling programming exercise for {{}}: {{}}", username, e.getMessage());
        }
        return requestStats;
    }

    private RequestStat submitStudentExam() {
        long start = System.nanoTime();
        webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "courses", courseIdString, "exams", examIdString, "student-exams", "submit").build()
            )
            .bodyValue(studentExam)
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, SUBMIT_STUDENT_EXAM);
    }

    private RequestStat loadExamSummary() {
        long start = System.nanoTime();
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
                        "summary"
                    )
                    .build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    @Nullable
    private static ModelingSubmission getModelingSubmission(ModelingExercise modelingExercise) {
        return getSubmissionOfType(modelingExercise, ModelingSubmission.class);
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

    private RequestStat commitAndPushRepo() throws IOException, GitAPIException {
        var localPath = Path.of("repos", username);
        log.debug("Commit and push to " + localPath);

        var git = Git.open(localPath.toFile());
        git.add().addFilepattern("src").call();
        git.commit().setMessage("local test").setAllowEmpty(true).setSign(false).call();

        long start = System.nanoTime();
        git.push().setCredentialsProvider(getCredentialsProvider()).call();
        long duration = System.nanoTime() - start;

        git.close();
        return new RequestStat(now(), duration, PUSH);
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

    private RequestStat cloneRepo(String repositoryUrl) throws IOException {
        log.debug("Clone " + repositoryUrl);

        var localPath = Path.of("repos", username);
        FileUtils.deleteDirectory(localPath.toFile());

        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                long start = System.nanoTime();
                var git = Git
                    .cloneRepository()
                    .setURI(repositoryUrl)
                    .setDirectory(localPath.toFile())
                    .setCredentialsProvider(getCredentialsProvider())
                    .call();
                var duration = System.nanoTime() - start;

                git.close();
                log.debug("Done " + repositoryUrl);
                return new RequestStat(now(), duration, CLONE);
            } catch (Exception e) {
                log.warn("Error while cloning repository for {{}}: {{}}", username, e.getMessage());
                attempt++;
                try {
                    sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        log.error("Failed to clone repository for {{}}", username);
        throw new RuntimeException("Failed to clone repository for " + username);
    }

    private UsernamePasswordCredentialsProvider getCredentialsProvider() {
        return new UsernamePasswordCredentialsProvider(username, password);
    }
}
