package de.tum.cit.aet.service.artemis.interaction;

import static de.tum.cit.aet.domain.RequestType.*;
import static de.tum.cit.aet.util.TimeLogUtil.formatDurationFrom;
import static java.lang.Thread.sleep;
import static java.time.ZonedDateTime.now;

import com.thedeanda.lorem.LoremIpsum;
import de.tum.cit.aet.artemisModel.*;
import de.tum.cit.aet.domain.ArtemisUser;
import de.tum.cit.aet.domain.OnlineIdeFileSubmission;
import de.tum.cit.aet.domain.RequestStat;
import de.tum.cit.aet.service.artemis.ArtemisUserService;
import de.tum.cit.aet.service.artemis.util.ArtemisServerInfo;
import de.tum.cit.aet.service.artemis.util.CourseDashboardDTO;
import de.tum.cit.aet.service.artemis.util.ScienceEventDTO;
import de.tum.cit.aet.service.artemis.util.UserSshPublicKeyDTO;
import de.tum.cit.aet.util.FileGeneratorUtil;
import de.tum.cit.aet.util.UMLClassDiagrams;
import jakarta.annotation.Nullable;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

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
    private String participationVcsAccessToken;
    private ArtemisAuthMechanism authenticationMechanism;

    private final int numberOfCommitsAndPushesFrom;
    private final int numberOfCommitsAndPushesTo;

    private boolean isScienceFeatureEnabled = false;
    private boolean isIrisEnabled = false;

    public SimulatedArtemisStudent(
        String artemisUrl,
        ArtemisUser artemisUser,
        ArtemisUserService artemisUserService,
        int numberOfCommitsAndPushesFrom,
        int numberOfCommitsAndPushesTo,
        ArtemisAuthMechanism authMechanism
    ) {
        super(artemisUrl, artemisUser, artemisUserService);
        log = LoggerFactory.getLogger(SimulatedArtemisStudent.class.getName() + "." + username);
        this.numberOfCommitsAndPushesFrom = numberOfCommitsAndPushesFrom;
        this.numberOfCommitsAndPushesTo = numberOfCommitsAndPushesTo;
        this.authenticationMechanism = authMechanism;
        // for old users in the DB which might never gotten a key pair generated
        if (artemisUser.getPublicKey() == null || artemisUser.getPrivateKey() == null) {
            var savedUser = artemisUserService.generateKeyPair(artemisUser);
            this.publicKeyString = savedUser.getPublicKey();
            this.privateKeyString = savedUser.getPrivateKey();
        } else {
            this.publicKeyString = artemisUser.getPublicKey();
            this.privateKeyString = artemisUser.getPrivateKey();
        }
    }

    @Override
    protected void checkAccess() {
        var response = webClient.get().uri("api/core/public/account").retrieve().bodyToMono(User.class).block();
        this.authenticated = response != null && response.getAuthorities().contains("ROLE_USER");
    }

    /**
     * Perform miscellaneous calls to Artemis, e.g. to get the user info, system notifications, account, notification settings, and courses.
     *
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
            getGlobalNotificationSettings(),
            getCourses(),
            configureSSH()
        );
    }

    /**
     * Participate in an exam, i.e. solve and submit the exercises and fetch live events.
     *
     * @param courseId the ID of the course
     * @param examId   the ID of the exam
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
     *
     * @param courseId the ID of the course
     * @param examId   the ID of the exam
     * @param courseProgrammingExerciseId the ID of the course programming exercise
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
        requestStats.add(getNotificationSettings());
        requestStats.add(getNotificationInfo());
        if (courseProgrammingExerciseId > 0) {
            if (isScienceFeatureEnabled) {
                requestStats.add(putScienceEvent(courseProgrammingExerciseId));
            }
            requestStats.add(getExerciseDetails(courseProgrammingExerciseId));
        }
        if (isIrisEnabled) {
            requestStats.addAll(List.of(
                getIrisStatus(),
                getIrisChatHistory(courseId)));
        }
        requestStats.add(navigateIntoExam());
        requestStats.add(getTestExams());
        requestStats.add(startExam());

        return requestStats;
    }

    /**
     * Submit and end an exam, i.e. submit the student exam and load the exam summary.
     *
     * @param courseId the ID of the course
     * @param examId   the ID of the exam
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
            isScienceFeatureEnabled = response.features().contains("Science");
            isIrisEnabled = response.activeProfiles().contains("iris");
        }
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getSystemNotifications() {
        long start = System.nanoTime();
        webClient.get().uri("api/core/public/system-notifications/active").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getAccount() {
        long start = System.nanoTime();
        webClient.get().uri("api/core/public/account").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getGlobalNotificationSettings() {
        long start = System.nanoTime();
        webClient.get().uri("api/communication/global-notification-settings").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat configureSSH() {
        long start = System.nanoTime();
        List<UserSshPublicKeyDTO> keys = webClient
            .get()
            .uri(uriBuilder -> uriBuilder.path("api/programming/ssh-settings/public-keys").build())
            .retrieve()
            .bodyToFlux(UserSshPublicKeyDTO.class)
            .collectList()
            .block();

        var hasArtemisKeyStoredAlready = keys.stream().anyMatch(key -> key.publicKey().equals(publicKeyString));

        if (!hasArtemisKeyStoredAlready) {
            try {
                webClient
                    .post()
                    .uri("api/programming/ssh-settings/public-key")
                    .bodyValue(UserSshPublicKeyDTO.of(publicKeyString))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            } catch (Exception e) {
                log.error("Error while adding SSH key for {{}}: {{}}", username, e.getMessage());
            }
        }

        return new RequestStat(now(), System.nanoTime() - start, SETUP_SSH_KEYS);
    }

    private RequestStat getCourses() {
        long start = System.nanoTime();
        webClient.get().uri("api/core/courses/for-dashboard").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getCourseDashboard(long exerciseId) {
        long start = System.nanoTime();
        CourseDashboardDTO courseDashboard = webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "core", "courses", courseIdString, "for-dashboard").build())
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
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "communication", "courses", courseIdString, "unread-messages").build())
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public RequestStat getNotificationSettings() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "communication", "notification", courseIdString, "settings").build())
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    public RequestStat getNotificationInfo() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "communication", "notification", "info").build())
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private void getExerciseChannelAndMessages(long exerciseId) {
        Map<String, Object> channelResponse = webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment("api", "communication", "courses", courseIdString, "exercises", String.valueOf(exerciseId), "channel")
                    .build()
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
                .uri(uriBuilder ->
                    uriBuilder
                        .pathSegment("api", "communication", "courses", courseIdString, "messages")
                        .queryParam("courseId", courseIdString)
                        .queryParam("conversationIds", channelId)
                        .queryParam("postSortCriterion", "CREATION_DATE")
                        .queryParam("sortingOrder", "DESCENDING")
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
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment(
                        "api",
                        "programming",
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
        webClient.get().uri("api/core/courses/for-dropdown").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getScienceSettings() {
        long start = System.nanoTime();
        webClient.get().uri("api/atlas/science-settings").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat putScienceEvent(long exerciseId) {
        long start = System.nanoTime();
        try {
            webClient
                .put()
                .uri("api/atlas/science")
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
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exercise", "exercises", String.valueOf(exerciseId), "details").build())
            .retrieve()
            .toBodilessEntity()
            .block();

        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat navigateIntoExam() {
        long start = System.nanoTime();
        StudentExam studentExam = webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "exam", "courses", courseIdString, "exams", examIdString, "own-student-exam").build()
            )
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
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exam", "courses", courseIdString, "test-exams-per-user").build())
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat startExam() {
        long start = System.nanoTime();
        studentExam = webClient
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
                uriBuilder
                    .pathSegment("api", "exam", "courses", courseIdString, "exams", examIdString, "student-exams", "live-events")
                    .build()
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
            } else if (exercise instanceof FileUploadExercise) {
                requestStats.addAll(solveAndSubmitFileUploadExercise((FileUploadExercise) exercise));
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
                    uriBuilder
                        .pathSegment("api", "modeling", "exercises", modelingExercise.getId().toString(), "modeling-submissions")
                        .build()
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
                .uri(uriBuilder ->
                    uriBuilder.pathSegment("api", "text", "exercises", textExercise.getId().toString(), "text-submissions").build()
                )
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
                    uriBuilder.pathSegment("api", "quiz", "exercises", quizExercise.getId().toString(), "submissions", "exam").build()
                )
                .bodyValue(quizSubmission)
                .retrieve()
                .toBodilessEntity()
                .block();
            return new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE);
        }
        return null;
    }

    private void commitAndPush(
        List<RequestStat> requestStats,
        ArtemisAuthMechanism mechanism,
        Long participationId,
        String changedFileContent
    ) throws IOException, GitAPIException, GeneralSecurityException {
        switch (mechanism) {
            case ONLINE_IDE -> makeOnlineIDECommitAndPush(requestStats, participationId, changedFileContent);
            default -> makeOfflineIDECommitAndPush(requestStats);
        }
    }

    private List<RequestStat> solveAndSubmitProgrammingExercise(ProgrammingExercise programmingExercise) {
        var programmingParticipation = (ProgrammingExerciseStudentParticipation) programmingExercise
            .getStudentParticipations()
            .iterator()
            .next();
        List<RequestStat> requestStats = new ArrayList<>();
        var repositoryCloneUrl = programmingParticipation.getRepositoryUri();
        var participationId = programmingParticipation.getId();
        requestStats.add(fetchParticipationVcsAccessToken(participationId));
        requestStats.add(fetchProgrammingIdeSettings());
        requestStats.add(postParticipation(programmingExercise.getId()));
        try {
            long start = System.nanoTime();

            switch (authenticationMechanism) {
                case ONLINE_IDE -> makeInitialProgrammingExerciseOnlineIDECalls(requestStats, participationId);
                case SSH -> requestStats.add(cloneRepoOverSSH(repositoryCloneUrl));
                default -> requestStats.add(cloneRepo(repositoryCloneUrl));
            }

            int n = new Random().nextInt(numberOfCommitsAndPushesFrom, numberOfCommitsAndPushesTo); // we do a random number of commits and pushes to make some noise
            log.info("Commit and push {}x for {}", n, username);
            for (int j = 0; j < n; j++) {
                sleep(100);
                var makeInvalidChange = new Random().nextBoolean();
                var writeToFile = !this.authenticationMechanism.equals(ArtemisAuthMechanism.ONLINE_IDE);
                var changedFileContent = changeFiles(makeInvalidChange, writeToFile);

                commitAndPush(requestStats, this.authenticationMechanism, participationId, changedFileContent);
            }
            log.debug("    Clone and commit+push done in " + formatDurationFrom(start));
        } catch (Exception e) {
            log.error("Error while handling programming exercise for {{}}: {{}}", username, e.getMessage());
        }
        return requestStats;
    }


private List<RequestStat> solveAndSubmitFileUploadExercise(FileUploadExercise fileUploadExercise) {
    List<RequestStat> requestStats = new ArrayList<>();
    long start = System.nanoTime();
    var participation = fileUploadExercise
        .getStudentParticipations()
        .iterator()
        .next();
    webClient
        .get()
        .uri(uriBuilder ->
            uriBuilder.pathSegment("api", "fileupload", "participations", participation.getId().toString(), "file-upload-editor").build()
        )
        .retrieve()
        .toBodilessEntity()
        .block();
    requestStats.add(new RequestStat(now(), System.nanoTime() - start, MISC));

    int fileSizeInBytes = 1024 * 1024; // 1 MB file size for file upload exercise
    ByteArrayResource file = FileGeneratorUtil.getDummyFile(fileSizeInBytes, "test-file.txt");
    MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
    multipartBody.add("file", file);
    multipartBody.add("submission", new FileUploadSubmission());

    start = System.nanoTime();
    webClient
        .post()
        .uri(uriBuilder ->
            uriBuilder.pathSegment("api", "fileupload", "exercises", fileUploadExercise.getId().toString(), "file-upload-submissions").build()
        )
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(BodyInserters.fromMultipartData(multipartBody))
        .retrieve()
        .toBodilessEntity()
        .block();
    // TODO maybe this should get a own RequestType to not skew the other submissions? File upload is likely inherently slower
    requestStats.add(new RequestStat(now(), System.nanoTime() - start, SUBMIT_EXERCISE));

    return requestStats;
}

    private RequestStat fetchParticipationVcsAccessToken(Long participationId) {
        long start = System.nanoTime();
        this.participationVcsAccessToken = webClient
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .pathSegment("api", "core", "account", "participation-vcs-access-token")
                    .queryParam("participationId", participationId)
                    .build()
            )
            .retrieve()
            .bodyToMono(String.class)
            .block();
        return new RequestStat(now(), System.nanoTime() - start, FETCH_PARTICIPATION_VCS_ACCESS_TOKEN);
    }

    private RequestStat fetchProgrammingIdeSettings() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "programming", "ide-settings").build())
            .retrieve()
            .bodyToMono(String.class)
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat submitStudentExam() {
        long start = System.nanoTime();
        webClient
            .post()
            .uri(uriBuilder ->
                uriBuilder.pathSegment("api", "exam", "courses", courseIdString, "exams", examIdString, "student-exams", "submit").build()
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
                        "exam",
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

    private RequestStat commitAndPushRepo() throws IOException, GitAPIException, GeneralSecurityException {
        var localPath = Path.of("repos", username);
        log.debug("Commit and push to " + localPath);

        var git = Git.open(localPath.toFile());
        git.add().addFilepattern("src").call();
        git.commit().setMessage("local test").setAllowEmpty(true).setSign(false).call();

        var keyPair = loadKeys(privateKeyString);
        long start = System.nanoTime();

        switch (this.authenticationMechanism) {
            case ONLINE_IDE -> throw new IllegalStateException("Cannot push to Online IDE via jgit");
            case PASSWORD -> git.push().setCredentialsProvider(getCredentialsProvider()).call();
            case PARTICIPATION_TOKEN -> git.push().setCredentialsProvider(getCredentialsProviderWithToken()).call();
            case SSH -> git
                .push()
                .setTransportConfigCallback(transport -> {
                    SshTransport sshTransport = (SshTransport) transport;
                    sshTransport.setSshSessionFactory(getSessionFactory(keyPair));
                })
                .call();
        }

        long duration = System.nanoTime() - start;

        git.close();

        return switch (this.authenticationMechanism) {
            case PASSWORD -> new RequestStat(now(), duration, PUSH_PASSWORD);
            case PARTICIPATION_TOKEN -> new RequestStat(now(), duration, PUSH_TOKEN);
            case SSH -> new RequestStat(now(), duration, PUSH_SSH);
            default -> new RequestStat(now(), duration, PUSH);
        };
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

    private void makeOfflineIDECommitAndPush(List<RequestStat> requestStats) throws IOException, GitAPIException, GeneralSecurityException {
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
            .uri("api/programming/repository/" + participationId + "/files?commit=yes")
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
            .uri(
                "api/programming/programming-exercise-participations/" +
                participationId +
                "/latest-result-with-feedbacks?withSubmission=true"
            )
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, PROGRAMMING_EXERCISE_RESULT);
    }

    private RequestStat fetchRepository(Long participationId) {
        long start = System.nanoTime();
        webClient.get().uri("api/programming/repository/" + participationId).retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, REPOSITORY_INFO);
    }

    private RequestStat fetchFile(Long participationId, String fileName) {
        long start = System.nanoTime();
        webClient
            .get()
            .uri("api/programming/repository/" + participationId + "/file?file=" + fileName)
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, REPOSITORY_FILES);
    }

    private RequestStat fetchFiles(Long participationId) {
        long start = System.nanoTime();
        webClient.get().uri("api/programming/repository/" + participationId + "/files").retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, REPOSITORY_FILES);
    }

    private RequestStat fetchPlantUml() {
        long start = System.nanoTime();
        String plantUmlString =
            "%40startuml%0A%0Aclass%20Client%20%7B%0A%7D%0A%0Aclass%20Policy%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2Bconfigure()%3C%2Fcolor%3E%0A%7D%0A%0Aclass%20Context%20%7B%0A%20%20%3Ccolor%3Agrey%3E-dates%3A%20List%3CDate%3E%3C%2Fcolor%3E%0A%20%20%3Ccolor%3Agrey%3E%2Bsort()%3C%2Fcolor%3E%0A%7D%0A%0Ainterface%20SortStrategy%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2BperformSort(List%3CDate%3E)%3C%2Fcolor%3E%0A%7D%0A%0Aclass%20BubbleSort%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2BperformSort(List%3CDate%3E)%3C%2Fcolor%3E%0A%7D%0A%0Aclass%20MergeSort%20%7B%0A%20%20%3Ccolor%3Agrey%3E%2BperformSort(List%3CDate%3E)%3C%2Fcolor%3E%0A%7D%0A%0AMergeSort%20-up-%7C%3E%20SortStrategy%20%23grey%0ABubbleSort%20-up-%7C%3E%20SortStrategy%20%23grey%0APolicy%20-right-%3E%20Context%20%23grey%3A%20context%0AContext%20-right-%3E%20SortStrategy%20%23grey%3A%20sortAlgorithm%0AClient%20.down.%3E%20Policy%0AClient%20.down.%3E%20Context%0A%0Ahide%20empty%20fields%0Ahide%20empty%20methods%0A%0A%40enduml&useDarkTheme=true";
        webClient.get().uri("api/programming/plantuml/svg?plantuml=" + plantUmlString).retrieve().toBodilessEntity().block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat cloneRepo(String repositoryUrl) throws IOException {
        log.debug("Clone " + repositoryUrl);

        var localPath = Path.of("repos", username);
        FileUtils.deleteDirectory(localPath.toFile());

        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                long start = System.nanoTime();
                UsernamePasswordCredentialsProvider credentialsProvider;
                switch (authenticationMechanism) {
                    case ONLINE_IDE -> throw new IOException("Cannot pull from Online IDE");
                    case PASSWORD -> credentialsProvider = getCredentialsProvider();
                    case PARTICIPATION_TOKEN -> credentialsProvider = getCredentialsProviderWithToken();
                    default -> throw new IllegalStateException("Not implemented");
                }

                var git = Git.cloneRepository()
                    .setURI(repositoryUrl)
                    .setDirectory(localPath.toFile())
                    .setCredentialsProvider(credentialsProvider)
                    .call();

                var duration = System.nanoTime() - start;
                git.close();
                log.debug("Done " + repositoryUrl);
                return switch (authenticationMechanism) {
                    case PASSWORD -> new RequestStat(now(), duration, CLONE_PASSWORD);
                    case PARTICIPATION_TOKEN -> new RequestStat(now(), duration, CLONE_TOKEN);
                    default -> new RequestStat(now(), duration, CLONE);
                };
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

    /**
     * Clones a repository over SSH using JGit and measures the time taken for the operation.
     *
     * @param repositoryUrl the URL of the repository to clone
     * @return a RequestStat containing the time taken for the clone operation
     * @throws IOException if an I/O error occurs
     * @throws GeneralSecurityException if a security error occurs
     */
    public RequestStat cloneRepoOverSSH(String repositoryUrl) throws IOException, GeneralSecurityException {
        log.debug("Clone " + repositoryUrl);

        var localPath = Path.of("repos", username);
        FileUtils.deleteDirectory(localPath.toFile());

        var sshRepositoryUrl = getSshCloneUrl(repositoryUrl);

        int attempt = 0;

        var keyPair = loadKeys(privateKeyString);
        while (attempt < MAX_RETRIES) {
            try {
                long start = System.nanoTime();

                Git git = Git.cloneRepository()
                    .setURI(sshRepositoryUrl)
                    .setDirectory(localPath.toFile())
                    .setTransportConfigCallback(transport -> {
                        SshTransport sshTransport = (SshTransport) transport;
                        sshTransport.setSshSessionFactory(getSessionFactory(keyPair));
                    })
                    .call();

                var duration = System.nanoTime() - start;

                git.close();

                return new RequestStat(now(), duration, CLONE_SSH);
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

    /**
     * Loads SSH keys from a given private key string.
     *
     * @param privateKey the private key in PEM format
     * @return an iterable of KeyPair objects
     * @throws IOException if an I/O error occurs
     * @throws GeneralSecurityException if a security error occurs
     */
    public Iterable<KeyPair> loadKeys(String privateKey) throws IOException, GeneralSecurityException {
        try {
            Object parsed = new PEMParser(new StringReader(privateKey)).readObject();
            KeyPair pair;
            pair = new JcaPEMKeyConverter().getKeyPair((PEMKeyPair) parsed);

            return Collections.singleton(pair);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load SSH keys", e);
        }
    }

    /**
     * Creates an SshdSessionFactory for SSH connections using the provided key pairs.
     *
     * @param keyPairs the key pairs to use for authentication
     * @return an SshdSessionFactory configured with the provided key pairs
     */
    private SshdSessionFactory getSessionFactory(Iterable<KeyPair> keyPairs) {
        // Create a temporary directory to use for the home directory and SSH directory
        // This is required by the SshdSessionFactory object despite us not using them
        Path temporaryDirectory;
        try {
            temporaryDirectory = Files.createTempDirectory("ssh-temp-dir-user-1");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary directory", e);
        }

        return new SshdSessionFactoryBuilder()
            .setPreferredAuthentications("publickey")
            .setDefaultKeysProvider(ignoredSshDirBecauseWeUseAnInMemorySetOfKeyPairs -> keyPairs)
            .setHomeDirectory(temporaryDirectory.toFile())
            .setSshDirectory(temporaryDirectory.toFile())
            .setServerKeyDatabase((ignoredHomeDir, ignoredSshDir) ->
                new ServerKeyDatabase() {
                    @Override
                    public List<PublicKey> lookup(String connectAddress, InetSocketAddress remoteAddress, Configuration config) {
                        return Collections.emptyList();
                    }

                    @Override
                    public boolean accept(
                        String connectAddress,
                        InetSocketAddress remoteAddress,
                        PublicKey serverKey,
                        Configuration config,
                        CredentialsProvider provider
                    ) {
                        return true;
                    }
                }
            )
            //The JGitKeyCache handles the caching of keys to avoid unnecessary disk I/O and improve performance
            .build(new JGitKeyCache());
    }

    /**
     * Create a participation for the given exercise if it does not exist yet.
     *
     * @param exerciseId the ID of the exercise
     * @return the participations for the given exercise
     */
    public RequestStat postParticipation(long exerciseId) {
        long start = System.nanoTime();
        webClient
            .post()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "exercise", "exercises", String.valueOf(exerciseId), "participations").build())
            .retrieve()
            .bodyToMono(Participation.class)
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private String getSshCloneUrl(String cloneUrl) {
        var artemisServerHostname = artemisUrl.substring(artemisUrl.indexOf("//") + 2).split("/")[0].split(":")[0];
        return "ssh://git@" + artemisServerHostname + ":7921" + cloneUrl.substring(cloneUrl.indexOf("/git/"));
    }

    private RequestStat getIrisStatus() {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "iris", "status").build())
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private RequestStat getIrisChatHistory(long courseId) {
        long start = System.nanoTime();
        webClient
            .get()
            .uri(uriBuilder -> uriBuilder.pathSegment("api", "iris", "chat-history", String.valueOf(courseId), "sessions").build())
            .retrieve()
            .toBodilessEntity()
            .block();
        return new RequestStat(now(), System.nanoTime() - start, MISC);
    }

    private UsernamePasswordCredentialsProvider getCredentialsProvider() {
        return new UsernamePasswordCredentialsProvider(username, password);
    }

    private UsernamePasswordCredentialsProvider getCredentialsProviderWithToken() {
        return new UsernamePasswordCredentialsProvider(username, participationVcsAccessToken);
    }
}
