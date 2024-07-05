package de.tum.cit.ase.service.artemis.interaction;

import static de.tum.cit.ase.domain.RequestType.*;
import static de.tum.cit.ase.util.TimeLogUtil.formatDurationFrom;
import static java.lang.Thread.sleep;
import static java.time.ZonedDateTime.now;

import com.thedeanda.lorem.LoremIpsum;
import de.tum.cit.ase.artemisModel.*;
import de.tum.cit.ase.domain.ArtemisUser;
import de.tum.cit.ase.domain.OnlineIdeFileSubmission;
import de.tum.cit.ase.domain.RequestStat;
import de.tum.cit.ase.service.artemis.ArtemisUserService;
import de.tum.cit.ase.service.artemis.util.ArtemisServerInfo;
import de.tum.cit.ase.service.artemis.util.CourseDashboardDTO;
import de.tum.cit.ase.service.artemis.util.ScienceEventDTO;
import de.tum.cit.ase.util.UMLClassDiagrams;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

/**
 * A simulated Artemis student that can be used to interact with the Artemis server.
 */
public class SimulatedArtemisStudent extends SimulatedArtemisUser {

    private static final int MAX_RETRIES = 4; // Maximum number of retries for clone
    private static final long RETRY_DELAY_MS = 5000; // Delay between clone retries in milliseconds

    private String courseIdString;
    private String examIdString;
    private Long studentExamId;
    private StudentExam studentExam;
    private final int numberOfCommitsAndPushesFrom;
    private final int numberOfCommitsAndPushesTo;

    private boolean isScienceFeatureEnabled = false;

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

        return List.of(
            getInfo(),
            getSystemNotifications(),
            getAccount(),
            getNotificationSettings(),
            getCourses(),
            getMutedConversations(),
            getNotifications()
        );
    }

    /**
     * Participate in an exam, i.e. solve and submit the exercises and fetch live events.
     * @param courseId the ID of the course
     * @param examId the ID of the exam
     * @return the list of request stats
     */
    public List<RequestStat> participateInExam(long courseId, long examId, boolean onlineIde) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }
        this.courseIdString = String.valueOf(courseId);
        this.examIdString = String.valueOf(examId);

        List<RequestStat> requestStats = new ArrayList<>();

        requestStats.add(fetchLiveEvents());
        requestStats.addAll(handleExercises(onlineIde));

        return requestStats;
    }

    /**
     * Start participating in an exam, i.e. navigate into the exam and start the exam.
     * @param courseId the ID of the course
     * @param examId the ID of the exam
     * @return the list of request stats
     */
    public List<RequestStat> startExamParticipation(long courseId, long examId) {
        return startExamParticipation(courseId, examId, 0);
    }

    /**
     * Start participating in an exam, i.e. navigate into the exam and start the exam.
     * @param courseId the ID of the course
     * @param examId the ID of the exam
     * @return the list of request stats
     */
    public List<RequestStat> startExamParticipation(long courseId, long examId, long courseProgrammingExerciseId) {
        if (!authenticated) {
            throw new IllegalStateException("User " + username + " is not logged in or not a student.");
        }
        this.courseIdString = String.valueOf(courseId);
        this.examIdString = String.valueOf(examId);

        List<RequestStat> requestStats = new ArrayList<>();

        requestStats.add(getCourseDashboard(courseProgrammingExerciseId));
        requestStats.add(getCoursesDropdown());
        requestStats.add(getScienceSettings());
        if (courseProgrammingExerciseId > 0) {
            if (isScienceFeatureEnabled) {
                requestStats.add(putScienceEvent(courseProgrammingExerciseId));
            }
            requestStats.add(getExerciseDetails(courseProgrammingExerciseId));
        }
        requestStats.add(navigateIntoExam());
        requestStats.add(getTestExams());
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
        ArtemisServerInfo response = webClient.get().uri("management/info").retrieve().bodyToMono(ArtemisServerInfo.class).block();
        if (response != null) {
            isScienceFeatureEnabled = response.features().contains("science");
        }
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

    private RequestStat getNotifications() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("api/notifications")
                        .queryParam("page", 0)
                        .queryParam("size", 25)
                        .queryParam("sort", "notificationDate,desc")
                        .build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getCourses() {
        long start = System.nanoTime();
        webClient.get().uri("api/courses/for-dashboard").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getMutedConversations() {
        long start = System.nanoTime();
        webClient.get().uri("api/muted-conversations").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getCourseDashboard(long exerciseId) {
        long start = System.nanoTime();
        CourseDashboardDTO courseDashboard = webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", courseIdString, "for-dashboard").build())
            .retrieve()
            .bodyToMono(CourseDashboardDTO.class)
            .block();

        if (courseDashboard == null) {
            return new RequestStat(now(), System.nanoTime() - start, MISC);
        }

        try {
            if (!courseDashboard.course().getCourseInformationSharingConfiguration().equals("DISABLED")) {
                getUnreadMessages();
                getExerciseChannelAndMessages(exerciseId);
            }

            if (courseDashboard.participationResults() != null) {
                for (CourseDashboardDTO.ParticipationResultDTO result : courseDashboard.participationResults()) {
                    long participationId = result.participationId();
                    getLatestResult(participationId);
                }
            }
        } catch (Exception e) {
            log.error("Error while getting course dashboard for {{}}: {{}}", username, e.getMessage());
        }

        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private void getUnreadMessages() {
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", courseIdString, "unread-messages").build())
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    private void getExerciseChannelAndMessages(long exerciseId) {
        Map<String, Object> channelResponse = webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder.pathSegment("api", "courses", courseIdString, "exercises", String.valueOf(exerciseId), "channel").build()
            )
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();
        final long channelId;
        if (channelResponse != null) {
            channelId = ((Number) channelResponse.get("id")).longValue();
            if (channelId == 0) {
                return;
            }

            webClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .pathSegment("api", "courses", courseIdString, "messages")
                            .queryParam("conversationId", channelId)
                            .queryParam("PostSortCriterion", "CREATION_DATE")
                            .queryParam("SortingOrder", "DESCENDING")
                            .queryParam("pagingEnabled", true)
                            .queryParam("page", 0)
                            .queryParam("size", 50)
                            .build()
                )
                .retrieve()
                .toBodilessEntity()
                .block();
        }
    }

    private void getLatestResult(long participationId) {
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .pathSegment(
                            "api",
                            "programming-exercise-participations",
                            String.valueOf(participationId),
                            "latest-pending-submission"
                        )
                        .build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    private RequestStat getCoursesDropdown() {
        long start = System.nanoTime();
        webClient.get().uri("api/courses/for-dropdown").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getScienceSettings() {
        long start = System.nanoTime();
        webClient.get().uri("api/science-settings").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat putScienceEvent(long exerciseId) {
        long start = System.nanoTime();
        try {
            webClient
                .put()
                .uri("api/science")
                .bodyValue(new ScienceEventDTO(ScienceEventDTO.ScienceEventType.EXERCISE__OPEN, exerciseId))
                .retrieve()
                .toBodilessEntity()
                .block();
        } catch (Exception e) {
            log.error("Error while putting science event for {{}}: {{}}", username, e.getMessage());
        }
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getExerciseDetails(long exerciseId) {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exercises", String.valueOf(exerciseId), "details").build())
            .retrieve()
            .toBodilessEntity()
            .block();

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

    private RequestStat getTestExams() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "courses", courseIdString, "test-exams-per-user").build())
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat startExam() {
        long start = System.nanoTime();
        studentExam = webClient
            .get()
            .uri(
                uriBuilder ->
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
            .uri(
                uriBuilder ->
                    uriBuilder.pathSegment("api", "courses", courseIdString, "exams", examIdString, "student-exams", "live-events").build()
            )
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private List<RequestStat> handleExercises(boolean onlineIde) {
        List<RequestStat> requestStats = new ArrayList<>();
        for (var exercise : studentExam.getExercises()) {
            if (exercise instanceof ModelingExercise) {
                requestStats.add(solveAndSubmitModelingExercise((ModelingExercise) exercise));
            } else if (exercise instanceof TextExercise) {
                requestStats.add(solveAndSubmitTextExercise((TextExercise) exercise));
            } else if (exercise instanceof QuizExercise) {
                requestStats.add(solveAndSubmitQuizExercise((QuizExercise) exercise));
            } else if (exercise instanceof ProgrammingExercise) {
                requestStats.addAll(solveAndSubmitProgrammingExercise((ProgrammingExercise) exercise, onlineIde));
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
                .uri(
                    uriBuilder ->
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
                .uri(
                    uriBuilder -> uriBuilder.pathSegment("api", "exercises", quizExercise.getId().toString(), "submissions", "exam").build()
                )
                .bodyValue(quizSubmission)
                .retrieve()
                .toBodilessEntity()
                .block();
            return new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE);
        }
        return null;
    }

    private void commitAndPush(List<RequestStat> requestStats, boolean onlineIde, Long participationId, String changedFileContent)
        throws IOException, GitAPIException {
        if (onlineIde) {
            makeOnlineIDECommitAndPush(requestStats, participationId, changedFileContent);
        } else {
            makeOfflineIDECommitAndPush(requestStats);
        }
    }

    private List<RequestStat> solveAndSubmitProgrammingExercise(ProgrammingExercise programmingExercise, boolean onlineIDE) {
        var programmingParticipation = (ProgrammingExerciseStudentParticipation) programmingExercise
            .getStudentParticipations()
            .iterator()
            .next();
        List<RequestStat> requestStats = new ArrayList<>();
        var repositoryCloneUrl = programmingParticipation.getRepositoryUri();
        var participationId = programmingParticipation.getId();

        try {
            long start = System.nanoTime();

            if (onlineIDE) {
                makeInitialProgrammingExerciseOnlineIDECalls(requestStats, participationId);
            } else {
                requestStats.add(cloneRepo(repositoryCloneUrl));
            }

            int n = new Random().nextInt(numberOfCommitsAndPushesFrom, numberOfCommitsAndPushesTo); // we do a random number of commits and pushes to make some noise
            log.info("Commit and push {}x for {}", n, username);
            for (int j = 0; j < n; j++) {
                sleep(100);
                var makeInvalidChange = new Random().nextBoolean();
                var changedFileContent = changeFiles(makeInvalidChange, false);

                commitAndPush(requestStats, onlineIDE, participationId, changedFileContent);
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
            .uri(
                uriBuilder ->
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
            .uri(
                uriBuilder ->
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

    private String changeFiles(boolean invalidChange, boolean writeToFile) throws IOException {
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
        if (invalidChange) {
            newContent += "}";
        }

        newContent = newContent.replace("$$1", String.valueOf(new Random().nextInt(100)));
        if (writeToFile) {
            FileUtils.writeStringToFile(bubbleSort.toFile(), newContent, Charset.defaultCharset());
        }
        return newContent;
    }

    private void makeInitialProgrammingExerciseOnlineIDECalls(List<RequestStat> requestStats, Long participationId) {
        requestStats.add(getLatestResultWithFeedback(participationId));
        requestStats.add(fetchRepository(participationId));
        requestStats.add(fetchPlantUml());
        requestStats.add(fetchFiles(participationId));
    }

    private void makeOfflineIDECommitAndPush(List<RequestStat> requestStats) throws IOException, GitAPIException {
        requestStats.add(commitAndPushRepo());
    }

    private void makeOnlineIDECommitAndPush(List<RequestStat> requestStats, Long participationId, String changedFileContent) {
        requestStats.add(fetchRepository(participationId));

        var fileName = String.join("/", "src", "progforbenchtemp", "BubbleSort.java");
        requestStats.add(fetchFile(participationId, fileName));

        log.debug("Commit and push to " + fileName);
        long start = System.nanoTime();
        webClient
            .put()
            .uri("api/repository/" + participationId + "/files?commit=yes")
            .bodyValue(List.of(new OnlineIdeFileSubmission(fileName, changedFileContent)))
            .retrieve()
            .toBodilessEntity()
            .block();
        long duration = System.nanoTime() - start;
        requestStats.add(new RequestStat(now(), duration, PUSH));
    }

    private RequestStat getLatestResultWithFeedback(Long participationId) {
        long start = System.nanoTime();
        webClient
            .get()
            .uri("api/programming-exercise-participations/" + participationId + "/latest-result-with-feedbacks?withSubmission=true")
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, PROGRAMMING_EXERCISE_RESULT);
    }

    private RequestStat fetchRepository(Long participationId) {
        long start = System.nanoTime();
        webClient.get().uri("api/repository/" + participationId).retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, REPOSITORY_INFO);
    }

    private RequestStat fetchFile(Long participationId, String fileName) {
        long start = System.nanoTime();
        webClient
            .get()
            .uri("api/repository/" + participationId + "/file?file=" + fileName)
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, REPOSITORY_FILE);
    }

    private RequestStat fetchFiles(Long participationId) {
        long start = System.nanoTime();
        webClient.get().uri("api/repository/" + participationId + "/files").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, REPOSITORY_FILES);
    }

    private RequestStat fetchPlantUml() {
        long start = System.nanoTime();
        String plantUmlString =
            "%40startuml%0A%0Aclass%20Client%20%7B%0A%7D%0A%0Aclass%20Policy%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2Bconfigure()%3C%2Fcolor%3E%0A%7D%0A%0Aclass%20Context%20%7B%0A%20%20%3Ccolor%3Agrey%3E-dates%3A%20List%3CDate%3E%3C%2Fcolor%3E%0A%20%20%3Ccolor%3Agrey%3E%2Bsort()%3C%2Fcolor%3E%0A%7D%0A%0Ainterface%20SortStrategy%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2BperformSort(List%3CDate%3E)%3C%2Fcolor%3E%0A%7D%0A%0Aclass%20BubbleSort%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2BperformSort(List%3CDate%3E)%3C%2Fcolor%3E%0A%7D%0A%0Aclass%20MergeSort%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2BperformSort(List%3CDate%3E)%3C%2Fcolor%3E%0A%7D%0A%0AMergeSort%20-up-%7C%3E%20SortStrategy%20%23grey%0ABubbleSort%20-up-%7C%3E%20SortStrategy%20%23grey%0APolicy%20-right-%3E%20Context%20%23grey%3A%20context%0AContext%20-right-%3E%20SortStrategy%20%23grey%3A%20sortAlgorithm%0AClient%20.down.%3E%20Policy%0AClient%20.down.%3E%20Context%0A%0Ahide%20empty%20fields%0Ahide%20empty%20methods%0A%0A%40enduml&useDarkTheme=true";
        webClient.get().uri("api/plantuml/svg?plantuml=" + plantUmlString).retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, REPOSITORY_FILES);
    }

    private RequestStat cloneRepo(String repositoryUrl) throws IOException {
        log.debug("Clone " + repositoryUrl);

        var localPath = Path.of("repos", username);
        FileUtils.deleteDirectory(localPath.toFile());

        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                long start = System.nanoTime();
                var git = Git.cloneRepository()
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
