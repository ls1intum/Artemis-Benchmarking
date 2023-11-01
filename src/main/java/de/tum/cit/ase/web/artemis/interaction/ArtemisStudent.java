package de.tum.cit.ase.web.artemis.interaction;

import static de.tum.cit.ase.web.artemis.RequestType.*;
import static de.tum.cit.ase.web.artemis.util.TimeLogUtil.formatDurationFrom;
import static de.tum.cit.ase.web.artemis.util.UMLClassDiagrams.CLASS_MODEL_1;
import static de.tum.cit.ase.web.artemis.util.UMLClassDiagrams.CLASS_MODEL_2;
import static java.lang.Thread.sleep;
import static java.time.ZonedDateTime.now;

import com.thedeanda.lorem.LoremIpsum;
import de.tum.cit.ase.artemisModel.*;
import de.tum.cit.ase.web.artemis.RequestStat;
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

public class ArtemisStudent extends ArtemisUser {

    private String courseIdString;
    private String examIdString;
    private Long studentExamId;
    private StudentExam studentExam;

    public ArtemisStudent(String username, String password, String artemisUrl) {
        super(username, password, artemisUrl);
        log = LoggerFactory.getLogger(ArtemisStudent.class.getName() + "." + username);
    }

    @Override
    protected void checkAccess() {
        var response = webClient.get().uri("api/public/account").retrieve().bodyToMono(User.class).block();
        this.authenticated = response != null && response.getAuthorities().contains("ROLE_STUDENT");
    }

    public List<RequestStat> performInitialCalls() {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }

        return List.of(getInfo(), getSystemNotifications(), getAccount(), getNotificationSettings(), getCourses());
    }

    public List<RequestStat> participateInExam(long courseId, long examId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }
        this.courseIdString = String.valueOf(courseId);
        this.examIdString = String.valueOf(examId);

        List<RequestStat> requestStats = new ArrayList<>();

        requestStats.add(navigateIntoExam());
        requestStats.add(startExam());
        requestStats.addAll(handleExercises());
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
                modelingSubmission.setModel(CLASS_MODEL_1);
                modelingSubmission.setExplanationText("The model describes ...");
            } else {
                modelingSubmission.setModel(CLASS_MODEL_2);
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
        var repositoryCloneUrl = programmingParticipation.getRepositoryUrl();
        List<RequestStat> requestStats = new ArrayList<>();

        try {
            long start = System.nanoTime();
            requestStats.add(cloneRepo(repositoryCloneUrl));

            int n = new Random().nextInt(10, 15); // we do a random number of commits and pushes to make some noise
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
        git.commit().setMessage("local test").setAllowEmpty(true).call();

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

    private RequestStat cloneRepo(String repositoryUrl) throws IOException, GitAPIException {
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
        var duration = System.nanoTime() - start;

        git.close();
        log.debug("Done " + repositoryUrl);
        return new RequestStat(now(), duration, CLONE);
    }

    private UsernamePasswordCredentialsProvider getCredentialsProvider() {
        return new UsernamePasswordCredentialsProvider(username, password);
    }
}
