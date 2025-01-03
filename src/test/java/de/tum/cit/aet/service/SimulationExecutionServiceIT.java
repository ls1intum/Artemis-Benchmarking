package de.tum.cit.aet.service;

import static de.tum.cit.aet.domain.SimulationRun.Status.*;
import static de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisUser.*;
import static de.tum.cit.aet.util.ArtemisServer.PRODUCTION;
import static de.tum.cit.aet.util.ArtemisServer.TS1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.IntegrationTest;
import de.tum.cit.aet.artemisModel.ArtemisAuthMechanism;
import de.tum.cit.aet.artemisModel.Course;
import de.tum.cit.aet.artemisModel.Exam;
import de.tum.cit.aet.domain.ArtemisUser;
import de.tum.cit.aet.domain.LogMessage;
import de.tum.cit.aet.domain.Simulation;
import de.tum.cit.aet.domain.SimulationRun;
import de.tum.cit.aet.repository.LogMessageRepository;
import de.tum.cit.aet.repository.SimulationRunRepository;
import de.tum.cit.aet.service.artemis.ArtemisConfiguration;
import de.tum.cit.aet.service.artemis.ArtemisUserService;
import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisAdmin;
import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisStudent;
import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisUser;
import de.tum.cit.aet.service.simulation.SimulationExecutionService;
import de.tum.cit.aet.service.simulation.SimulationFailedException;
import de.tum.cit.aet.service.simulation.SimulationResultService;
import de.tum.cit.aet.util.ArtemisAccountDTO;
import de.tum.cit.aet.web.websocket.SimulationWebsocketService;
import jakarta.transaction.Transactional;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@IntegrationTest
@Transactional
public class SimulationExecutionServiceIT {

    @Autowired
    @MockitoSpyBean
    private SimulationExecutionService simulationExecutionService;

    @MockitoBean
    private ArtemisUserService artemisUserService;

    @MockitoBean
    private SimulationWebsocketService simulationWebsocketService;

    @MockitoBean
    private SimulationResultService simulationResultService;

    @MockitoBean
    private SimulationRunRepository simulationRunRepository;

    @MockitoBean
    private LogMessageRepository logMessageRepository;

    @MockitoSpyBean
    private ArtemisConfiguration artemisConfiguration;

    @Mock
    private SimulatedArtemisAdmin simulatedArtemisAdmin;

    @Mock
    private SimulatedArtemisStudent simulatedArtemisStudent1;

    @Mock
    private SimulatedArtemisStudent simulatedArtemisStudent2;

    @Mock
    private SimulatedArtemisStudent simulatedArtemisStudent3;

    private ArtemisUser adminUser;
    private ArtemisUser studentUser1;
    private ArtemisUser studentUser2;
    private ArtemisUser studentUser3;

    private Course course;
    private Exam exam;
    private MockedStatic<SimulatedArtemisUser> mockedSimulatedArtemisUser;
    private List<SimulationRun.Status> statusesOnWebsocketUpdate;

    @BeforeEach
    public void init() {
        simulationExecutionService.setDoNotSleep(true);

        adminUser = new ArtemisUser();
        adminUser.setUsername("admin");
        adminUser.setPassword("admin");
        adminUser.setServer(TS1);
        adminUser.setServerWideId(0);

        studentUser1 = new ArtemisUser();
        studentUser1.setUsername("student1");
        studentUser1.setPassword("student1");
        studentUser1.setServer(TS1);
        studentUser1.setServerWideId(1);

        studentUser2 = new ArtemisUser();
        studentUser2.setUsername("student2");
        studentUser2.setPassword("student2");
        studentUser2.setServer(TS1);
        studentUser2.setServerWideId(2);

        studentUser3 = new ArtemisUser();
        studentUser3.setUsername("student3");
        studentUser3.setPassword("student3");
        studentUser3.setServer(TS1);
        studentUser3.setServerWideId(3);

        when(artemisUserService.getAdminUser(any())).thenReturn(adminUser);
        when(artemisUserService.getUsersFromRange(any(), eq("1-3"))).thenReturn(List.of(studentUser1, studentUser2, studentUser3));

        mockedSimulatedArtemisUser = mockStatic(SimulatedArtemisUser.class);
        mockedSimulatedArtemisUser
            .when(() -> createArtemisAdminFromUser("", adminUser, artemisUserService))
            .thenReturn(simulatedArtemisAdmin);
        mockedSimulatedArtemisUser.when(() -> createArtemisAdminFromCredentials("", "admin", "admin")).thenReturn(simulatedArtemisAdmin);
        mockedSimulatedArtemisUser
            .when(() -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE))
            .thenReturn(simulatedArtemisStudent1);
        mockedSimulatedArtemisUser
            .when(() -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE))
            .thenReturn(simulatedArtemisStudent2);
        mockedSimulatedArtemisUser
            .when(() -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE))
            .thenReturn(simulatedArtemisStudent3);

        when(simulatedArtemisAdmin.login()).thenReturn(List.of());
        when(simulatedArtemisStudent1.login()).thenReturn(List.of());
        when(simulatedArtemisStudent2.login()).thenReturn(List.of());
        when(simulatedArtemisStudent3.login()).thenReturn(List.of());

        when(simulatedArtemisStudent1.performInitialCalls()).thenReturn(List.of());
        when(simulatedArtemisStudent2.performInitialCalls()).thenReturn(List.of());
        when(simulatedArtemisStudent3.performInitialCalls()).thenReturn(List.of());

        when(simulatedArtemisStudent1.startExamParticipation(1, 1, 0)).thenReturn(List.of());
        when(simulatedArtemisStudent2.startExamParticipation(1, 1, 0)).thenReturn(List.of());
        when(simulatedArtemisStudent3.startExamParticipation(1, 1, 0)).thenReturn(List.of());

        when((simulatedArtemisStudent1.participateInExam(1, 1))).thenReturn(List.of());
        when((simulatedArtemisStudent2.participateInExam(1, 1))).thenReturn(List.of());
        when((simulatedArtemisStudent3.participateInExam(1, 1))).thenReturn(List.of());

        when(simulatedArtemisStudent1.startExamParticipation(1, 1, 0)).thenReturn(List.of());
        when(simulatedArtemisStudent2.startExamParticipation(1, 1, 0)).thenReturn(List.of());
        when(simulatedArtemisStudent3.startExamParticipation(1, 1, 0)).thenReturn(List.of());

        course = new Course();
        course.setId(1L);
        when(simulatedArtemisAdmin.createCourse()).thenReturn(course);
        when(simulatedArtemisAdmin.getCourse(1)).thenReturn(course);

        exam = new Exam();
        exam.setCourse(course);
        exam.setId(1L);
        when(simulatedArtemisAdmin.createExam(course)).thenReturn(exam);

        when(logMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationResultService.calculateAndSaveResult(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        statusesOnWebsocketUpdate = new LinkedList<>();
        doAnswer(invocation -> {
            statusesOnWebsocketUpdate.add(invocation.getArgument(0, SimulationRun.class).getStatus());
            return null;
        })
            .when(simulationWebsocketService)
            .sendRunStatusUpdate(any());
    }

    @AfterEach
    public void cleanup() {
        mockedSimulatedArtemisUser.close();
    }

    @Test
    public void testCreateCourseAndExam_cleanupEnabled_success() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setOnlineIdePercentage(100);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);

        simulationExecutionService.simulateExam(run);

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(1)).createCourse();
        verify(simulatedArtemisAdmin, times(0)).getCourse(anyLong());
        verify(simulatedArtemisAdmin, times(1)).createExam(course);
        verify(simulatedArtemisAdmin, times(1)).createExamExercises(1, exam);
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForCourse(
            1,
            new SimulatedArtemisStudent[] { simulatedArtemisStudent1, simulatedArtemisStudent2, simulatedArtemisStudent3 }
        );
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForExam(1, 1);
        verify(simulatedArtemisAdmin, times(1)).prepareExam(1, 1);
        verify(simulatedArtemisAdmin, times(1)).cancelAllQueuedBuildJobs();
        verify(simulatedArtemisAdmin, times(1)).cancelAllRunningBuildJobs();
        // To give Artemis time to cancel all build jobs we extend the timeout beyond 5 seconds
        verify(simulatedArtemisAdmin, timeout(13000).times(1)).deleteCourse(1);
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(1)).login();
            verify(simulatedStudent, times(1)).performInitialCalls();
            verify(simulatedStudent, times(1)).startExamParticipation(1, 1, 0);
            verify(simulatedStudent, times(1)).participateInExam(1, 1);
            verify(simulatedStudent, times(1)).submitAndEndExam(1, 1);
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FINISHED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(1)).sendSimulationResult(run);
        verify(simulationWebsocketService, times(0)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(1)).calculateAndSaveResult(eq(run), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testCreateCourseAndExam_cleanupDisabled_success() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);
        run.setStatus(QUEUED);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(false);

        simulationExecutionService.simulateExam(run);

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(1)).createCourse();
        verify(simulatedArtemisAdmin, times(0)).getCourse(anyLong());
        verify(simulatedArtemisAdmin, times(1)).createExam(course);
        verify(simulatedArtemisAdmin, times(1)).createExamExercises(1, exam);
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForCourse(
            1,
            new SimulatedArtemisStudent[] { simulatedArtemisStudent1, simulatedArtemisStudent2, simulatedArtemisStudent3 }
        );
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForExam(1, 1);
        verify(simulatedArtemisAdmin, times(1)).prepareExam(1, 1);
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(1)).login();
            verify(simulatedStudent, times(1)).performInitialCalls();
            verify(simulatedStudent, times(1)).startExamParticipation(1, 1, 0);
            verify(simulatedStudent, times(1)).participateInExam(1, 1);
            verify(simulatedStudent, times(1)).submitAndEndExam(1, 1);
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FINISHED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(1)).sendSimulationResult(run);
        verify(simulationWebsocketService, times(0)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(1)).calculateAndSaveResult(eq(run), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testExistingCourseCreateExam_cleanupEnabled_success() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_CREATE_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setCourseId(1L);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);

        simulationExecutionService.simulateExam(run);

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(1)).getCourse(1);
        verify(simulatedArtemisAdmin, times(1)).createExam(course);
        verify(simulatedArtemisAdmin, times(1)).createExamExercises(1, exam);
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForExam(1, 1);
        verify(simulatedArtemisAdmin, times(1)).prepareExam(1, 1);
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(1)).deleteExam(1, 1);

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(1)).login();
            verify(simulatedStudent, times(1)).performInitialCalls();
            verify(simulatedStudent, times(1)).startExamParticipation(1, 1, 0);
            verify(simulatedStudent, times(1)).participateInExam(1, 1);
            verify(simulatedStudent, times(1)).submitAndEndExam(1, 1);
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FINISHED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(1)).sendSimulationResult(run);
        verify(simulationWebsocketService, times(0)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(1)).calculateAndSaveResult(eq(run), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testExistingCourseCreateExam_cleanupDisabled_success() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_CREATE_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setCourseId(1L);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(false);

        simulationExecutionService.simulateExam(run);

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(1)).getCourse(1);
        verify(simulatedArtemisAdmin, times(1)).createExam(course);
        verify(simulatedArtemisAdmin, times(1)).createExamExercises(1, exam);
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForExam(1, 1);
        verify(simulatedArtemisAdmin, times(1)).prepareExam(1, 1);
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(1)).login();
            verify(simulatedStudent, times(1)).performInitialCalls();
            verify(simulatedStudent, times(1)).startExamParticipation(1, 1, 0);
            verify(simulatedStudent, times(1)).participateInExam(1, 1);
            verify(simulatedStudent, times(1)).submitAndEndExam(1, 1);
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FINISHED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(1)).sendSimulationResult(run);
        verify(simulationWebsocketService, times(0)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(1)).calculateAndSaveResult(eq(run), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testExistingCourseUnpreparedExam_success() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_UNPREPARED_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setCourseId(1L);
        simulation.setExamId(1L);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);

        simulationExecutionService.simulateExam(run);

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(1)).getCourse(1);
        verify(simulatedArtemisAdmin, times(0)).createExam(any());
        verify(simulatedArtemisAdmin, times(0)).createExamExercises(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, times(1)).prepareExam(1, 1);
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(1)).login();
            verify(simulatedStudent, times(1)).performInitialCalls();
            verify(simulatedStudent, times(1)).startExamParticipation(1, 1, 0);
            verify(simulatedStudent, times(1)).participateInExam(1, 1);
            verify(simulatedStudent, times(1)).submitAndEndExam(1, 1);
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FINISHED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(1)).sendSimulationResult(run);
        verify(simulationWebsocketService, times(0)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(1)).calculateAndSaveResult(eq(run), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testExistingCoursePreparedExam_success() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_PREPARED_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setCourseId(1L);
        simulation.setExamId(1L);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);

        simulationExecutionService.simulateExam(run);

        verify(artemisUserService, times(0)).getAdminUser(any());

        verify(simulatedArtemisAdmin, times(0)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(0)).getCourse(1);
        verify(simulatedArtemisAdmin, times(0)).createExam(any());
        verify(simulatedArtemisAdmin, times(0)).createExamExercises(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, times(0)).prepareExam(1, 1);
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(1)).login();
            verify(simulatedStudent, times(1)).performInitialCalls();
            verify(simulatedStudent, times(1)).startExamParticipation(1, 1, 0);
            verify(simulatedStudent, times(1)).participateInExam(1, 1);
            verify(simulatedStudent, times(1)).submitAndEndExam(1, 1);
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FINISHED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(1)).sendSimulationResult(run);
        verify(simulationWebsocketService, times(0)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(1)).calculateAndSaveResult(eq(run), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser(any(), any(), any()), times(0));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testCreateCourseAndExam_cleanupEnabled_production_success() {
        Simulation simulation = new Simulation();
        simulation.setServer(PRODUCTION);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        ArtemisAccountDTO accountDTO = new ArtemisAccountDTO();
        accountDTO.setUsername("admin");
        accountDTO.setPassword("admin");
        run.setAdminAccount(accountDTO);

        when(artemisConfiguration.getCleanup(PRODUCTION)).thenReturn(true);

        simulationExecutionService.simulateExam(run);

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(1)).createCourse();
        verify(simulatedArtemisAdmin, times(0)).getCourse(anyLong());
        verify(simulatedArtemisAdmin, times(1)).createExam(course);
        verify(simulatedArtemisAdmin, times(1)).createExamExercises(1, exam);
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForCourse(
            1,
            new SimulatedArtemisStudent[] { simulatedArtemisStudent1, simulatedArtemisStudent2, simulatedArtemisStudent3 }
        );
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForExam(1, 1);
        verify(simulatedArtemisAdmin, times(1)).prepareExam(1, 1);
        verify(simulatedArtemisAdmin, timeout(1000).times(1)).deleteCourse(1);
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(1)).login();
            verify(simulatedStudent, times(1)).performInitialCalls();
            verify(simulatedStudent, times(1)).startExamParticipation(1, 1, 0);
            verify(simulatedStudent, times(1)).participateInExam(1, 1);
            verify(simulatedStudent, times(1)).submitAndEndExam(1, 1);
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FINISHED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(1)).sendSimulationResult(run);
        verify(simulationWebsocketService, times(0)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(1)).calculateAndSaveResult(eq(run), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser(any(), any(), any()), times(0));
        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromCredentials("", "admin", "admin"), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testCreateCourseAndExam_fail_onInitializeStudents() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);
        when(artemisUserService.getUsersFromRange(any(), eq("1-3"))).thenThrow(new RuntimeException("Test exception"));

        assertThrows(SimulationFailedException.class, () -> simulationExecutionService.simulateExam(run));

        verify(simulatedArtemisAdmin, times(0)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(0)).getCourse(anyLong());
        verify(simulatedArtemisAdmin, times(0)).createExam(any());
        verify(simulatedArtemisAdmin, times(0)).createExamExercises(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, times(0)).prepareExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(0)).login();
            verify(simulatedStudent, times(0)).performInitialCalls();
            verify(simulatedStudent, times(0)).startExamParticipation(anyLong(), anyLong(), anyLong());
            verify(simulatedStudent, times(0)).participateInExam(anyLong(), anyLong());
            verify(simulatedStudent, times(0)).submitAndEndExam(anyLong(), anyLong());
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FAILED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(0)).sendSimulationResult(any());
        verify(simulationWebsocketService, times(1)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(0)).calculateAndSaveResult(any(), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser(any(), any(), any()), times(0));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent(any(), any(), any(), anyInt(), anyInt(), ArtemisAuthMechanism.ONLINE_IDE),
            times(0)
        );
    }

    @Test
    public void testCreateCourseAndExam_fail_onInitializeAdmin() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);
        when(artemisUserService.getAdminUser(any())).thenReturn(null);

        assertThrows(SimulationFailedException.class, () -> simulationExecutionService.simulateExam(run));

        verify(simulatedArtemisAdmin, times(0)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(0)).getCourse(anyLong());
        verify(simulatedArtemisAdmin, times(0)).createExam(any());
        verify(simulatedArtemisAdmin, times(0)).createExamExercises(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, times(0)).prepareExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(0)).login();
            verify(simulatedStudent, times(0)).performInitialCalls();
            verify(simulatedStudent, times(0)).startExamParticipation(anyLong(), anyLong(), anyLong());
            verify(simulatedStudent, times(0)).participateInExam(anyLong(), anyLong());
            verify(simulatedStudent, times(0)).submitAndEndExam(anyLong(), anyLong());
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FAILED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(0)).sendSimulationResult(any());
        verify(simulationWebsocketService, times(1)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(0)).calculateAndSaveResult(any(), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser(any(), any(), any()), times(0));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testCreateCourseAndExam_fail_onCreateCourse() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);
        when(simulatedArtemisAdmin.createCourse()).thenThrow(new RuntimeException("Test exception"));

        assertThrows(SimulationFailedException.class, () -> simulationExecutionService.simulateExam(run));

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(1)).createCourse();
        verify(simulatedArtemisAdmin, times(0)).getCourse(anyLong());
        verify(simulatedArtemisAdmin, times(0)).createExam(any());
        verify(simulatedArtemisAdmin, times(0)).createExamExercises(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, times(0)).prepareExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(0)).login();
            verify(simulatedStudent, times(0)).performInitialCalls();
            verify(simulatedStudent, times(0)).startExamParticipation(anyLong(), anyLong(), anyLong());
            verify(simulatedStudent, times(0)).participateInExam(anyLong(), anyLong());
            verify(simulatedStudent, times(0)).submitAndEndExam(anyLong(), anyLong());
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FAILED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(0)).sendSimulationResult(any());
        verify(simulationWebsocketService, times(1)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(0)).calculateAndSaveResult(any(), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testCreateCourseAndExam_fail_onRegisterStudentsForCourse() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.CREATE_COURSE_AND_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);
        doThrow(new RuntimeException("Test exception")).when(simulatedArtemisAdmin).registerStudentsForCourse(anyLong(), any());

        assertThrows(SimulationFailedException.class, () -> simulationExecutionService.simulateExam(run));

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(1)).createCourse();
        verify(simulatedArtemisAdmin, times(0)).getCourse(anyLong());
        verify(simulatedArtemisAdmin, times(0)).createExam(any());
        verify(simulatedArtemisAdmin, times(0)).createExamExercises(anyLong(), any());
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForCourse(
            1,
            new SimulatedArtemisStudent[] { simulatedArtemisStudent1, simulatedArtemisStudent2, simulatedArtemisStudent3 }
        );
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, times(0)).prepareExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(1)).deleteCourse(1);
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(0)).login();
            verify(simulatedStudent, times(0)).performInitialCalls();
            verify(simulatedStudent, times(0)).startExamParticipation(anyLong(), anyLong(), anyLong());
            verify(simulatedStudent, times(0)).participateInExam(anyLong(), anyLong());
            verify(simulatedStudent, times(0)).submitAndEndExam(anyLong(), anyLong());
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FAILED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(0)).sendSimulationResult(any());
        verify(simulationWebsocketService, times(1)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(0)).calculateAndSaveResult(any(), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testExistingCourseCreateExam_fail_onGetCourse() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_CREATE_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setCourseId(1L);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);
        when(simulatedArtemisAdmin.getCourse(anyLong())).thenThrow(new RuntimeException("Test exception"));

        assertThrows(SimulationFailedException.class, () -> simulationExecutionService.simulateExam(run));

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(1)).getCourse(1);
        verify(simulatedArtemisAdmin, times(0)).createExam(any());
        verify(simulatedArtemisAdmin, times(0)).createExamExercises(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, times(0)).prepareExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(0)).login();
            verify(simulatedStudent, times(0)).performInitialCalls();
            verify(simulatedStudent, times(0)).startExamParticipation(anyLong(), anyLong(), anyLong());
            verify(simulatedStudent, times(0)).participateInExam(anyLong(), anyLong());
            verify(simulatedStudent, times(0)).submitAndEndExam(anyLong(), anyLong());
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FAILED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(0)).sendSimulationResult(any());
        verify(simulationWebsocketService, times(1)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(0)).calculateAndSaveResult(any(), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testExistingCourseCreateExam_fail_onCreateExam() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_CREATE_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setCourseId(1L);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);
        when(simulatedArtemisAdmin.createExam(any())).thenThrow(new RuntimeException("Test exception"));

        assertThrows(SimulationFailedException.class, () -> simulationExecutionService.simulateExam(run));

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(1)).getCourse(1);
        verify(simulatedArtemisAdmin, times(1)).createExam(course);
        verify(simulatedArtemisAdmin, times(0)).createExamExercises(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, times(0)).prepareExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteExam(anyLong(), anyLong());

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(0)).login();
            verify(simulatedStudent, times(0)).performInitialCalls();
            verify(simulatedStudent, times(0)).startExamParticipation(anyLong(), anyLong(), anyLong());
            verify(simulatedStudent, times(0)).participateInExam(anyLong(), anyLong());
            verify(simulatedStudent, times(0)).submitAndEndExam(anyLong(), anyLong());
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FAILED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(0)).sendSimulationResult(any());
        verify(simulationWebsocketService, times(1)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(0)).calculateAndSaveResult(any(), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testExistingCourseCreateExam_fail_onCreateExamExercises() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_CREATE_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setCourseId(1L);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);
        doThrow(new RuntimeException("Test exception")).when(simulatedArtemisAdmin).createExamExercises(anyLong(), any());

        assertThrows(SimulationFailedException.class, () -> simulationExecutionService.simulateExam(run));

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(1)).getCourse(1);
        verify(simulatedArtemisAdmin, times(1)).createExam(course);
        verify(simulatedArtemisAdmin, times(1)).createExamExercises(1, exam);
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, times(0)).prepareExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(1)).deleteExam(1, 1);

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(0)).login();
            verify(simulatedStudent, times(0)).performInitialCalls();
            verify(simulatedStudent, times(0)).startExamParticipation(anyLong(), anyLong(), anyLong());
            verify(simulatedStudent, times(0)).participateInExam(anyLong(), anyLong());
            verify(simulatedStudent, times(0)).submitAndEndExam(anyLong(), anyLong());
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FAILED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(0)).sendSimulationResult(any());
        verify(simulationWebsocketService, times(1)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(0)).calculateAndSaveResult(any(), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testExistingCourseCreateExam_fail_onRegisterStudentsForExam() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_CREATE_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setCourseId(1L);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);
        doThrow(new RuntimeException("Test exception")).when(simulatedArtemisAdmin).registerStudentsForExam(anyLong(), anyLong());

        assertThrows(SimulationFailedException.class, () -> simulationExecutionService.simulateExam(run));

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(1)).getCourse(1);
        verify(simulatedArtemisAdmin, times(1)).createExam(course);
        verify(simulatedArtemisAdmin, times(1)).createExamExercises(1, exam);
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForExam(1, 1);
        verify(simulatedArtemisAdmin, times(0)).prepareExam(anyLong(), anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(1)).deleteExam(1, 1);

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(0)).login();
            verify(simulatedStudent, times(0)).performInitialCalls();
            verify(simulatedStudent, times(0)).startExamParticipation(anyLong(), anyLong(), anyLong());
            verify(simulatedStudent, times(0)).participateInExam(anyLong(), anyLong());
            verify(simulatedStudent, times(0)).submitAndEndExam(anyLong(), anyLong());
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FAILED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(0)).sendSimulationResult(any());
        verify(simulationWebsocketService, times(1)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(0)).calculateAndSaveResult(any(), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testExistingCourseCreateExam_fail_onPrepareExam() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_CREATE_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setCourseId(1L);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);
        doThrow(new RuntimeException("Test exception")).when(simulatedArtemisAdmin).prepareExam(anyLong(), anyLong());

        assertThrows(SimulationFailedException.class, () -> simulationExecutionService.simulateExam(run));

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(1)).getCourse(1);
        verify(simulatedArtemisAdmin, times(1)).createExam(course);
        verify(simulatedArtemisAdmin, times(1)).createExamExercises(1, exam);
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForExam(1, 1);
        verify(simulatedArtemisAdmin, times(1)).prepareExam(1, 1);
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(1)).deleteExam(1, 1);

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(0)).login();
            verify(simulatedStudent, times(0)).performInitialCalls();
            verify(simulatedStudent, times(0)).startExamParticipation(anyLong(), anyLong(), anyLong());
            verify(simulatedStudent, times(0)).participateInExam(anyLong(), anyLong());
            verify(simulatedStudent, times(0)).submitAndEndExam(anyLong(), anyLong());
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FAILED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(0)).sendSimulationResult(any());
        verify(simulationWebsocketService, times(1)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(0)).calculateAndSaveResult(any(), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testExistingCourseCreateExam_success_studentsCannotConnect() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_CREATE_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setCourseId(1L);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);

        when(simulatedArtemisStudent1.login()).thenThrow(new RuntimeException("Test exception"));
        when(simulatedArtemisStudent2.login()).thenThrow(new RuntimeException("Test exception"));
        when(simulatedArtemisStudent3.login()).thenThrow(new RuntimeException("Test exception"));
        when(simulatedArtemisStudent1.performInitialCalls()).thenThrow(new RuntimeException("Test exception"));
        when(simulatedArtemisStudent2.performInitialCalls()).thenThrow(new RuntimeException("Test exception"));
        when(simulatedArtemisStudent3.performInitialCalls()).thenThrow(new RuntimeException("Test exception"));
        when(simulatedArtemisStudent1.startExamParticipation(anyLong(), anyLong(), anyLong())).thenThrow(
            new RuntimeException("Test exception")
        );
        when(simulatedArtemisStudent2.startExamParticipation(anyLong(), anyLong(), anyLong())).thenThrow(
            new RuntimeException("Test exception")
        );
        when(simulatedArtemisStudent3.startExamParticipation(anyLong(), anyLong(), anyLong())).thenThrow(
            new RuntimeException("Test exception")
        );
        when(simulatedArtemisStudent1.participateInExam(anyLong(), anyLong())).thenThrow(new RuntimeException("Test exception"));
        when(simulatedArtemisStudent2.participateInExam(anyLong(), anyLong())).thenThrow(new RuntimeException("Test exception"));
        when(simulatedArtemisStudent3.participateInExam(anyLong(), anyLong())).thenThrow(new RuntimeException("Test exception"));
        when(simulatedArtemisStudent1.submitAndEndExam(anyLong(), anyLong())).thenThrow(new RuntimeException("Test exception"));
        when(simulatedArtemisStudent2.submitAndEndExam(anyLong(), anyLong())).thenThrow(new RuntimeException("Test exception"));
        when(simulatedArtemisStudent3.submitAndEndExam(anyLong(), anyLong())).thenThrow(new RuntimeException("Test exception"));

        simulationExecutionService.simulateExam(run);

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(1)).getCourse(1);
        verify(simulatedArtemisAdmin, times(1)).createExam(course);
        verify(simulatedArtemisAdmin, times(1)).createExamExercises(1, exam);
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForExam(1, 1);
        verify(simulatedArtemisAdmin, times(1)).prepareExam(1, 1);
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(1)).deleteExam(1, 1);

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(1)).login();
            verify(simulatedStudent, times(1)).performInitialCalls();
            verify(simulatedStudent, times(1)).startExamParticipation(1, 1, 0);
            verify(simulatedStudent, times(1)).participateInExam(1, 1);
            verify(simulatedStudent, times(1)).submitAndEndExam(1, 1);
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FINISHED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(1)).sendSimulationResult(run);
        verify(simulationWebsocketService, times(0)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(1)).calculateAndSaveResult(eq(run), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }

    @Test
    public void testExistingCourseCreateExam_success_failOnCleanup() {
        Simulation simulation = new Simulation();
        simulation.setServer(TS1);
        simulation.setNumberOfUsers(3);
        simulation.setMode(Simulation.Mode.EXISTING_COURSE_CREATE_EXAM);
        simulation.setCustomizeUserRange(false);
        simulation.setNumberOfCommitsAndPushesFrom(8);
        simulation.setNumberOfCommitsAndPushesTo(15);
        simulation.setCourseId(1L);

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);

        when(artemisConfiguration.getCleanup(TS1)).thenReturn(true);
        doThrow(new RuntimeException("Test exception")).when(simulatedArtemisAdmin).deleteExam(anyLong(), anyLong());

        simulationExecutionService.simulateExam(run);

        verify(simulatedArtemisAdmin, times(1)).login();
        verify(simulatedArtemisAdmin, times(0)).createCourse();
        verify(simulatedArtemisAdmin, times(1)).getCourse(1);
        verify(simulatedArtemisAdmin, times(1)).createExam(course);
        verify(simulatedArtemisAdmin, times(1)).createExamExercises(1, exam);
        verify(simulatedArtemisAdmin, times(0)).registerStudentsForCourse(anyLong(), any());
        verify(simulatedArtemisAdmin, times(1)).registerStudentsForExam(1, 1);
        verify(simulatedArtemisAdmin, times(1)).prepareExam(1, 1);
        verify(simulatedArtemisAdmin, timeout(1000).times(0)).deleteCourse(anyLong());
        verify(simulatedArtemisAdmin, timeout(1000).times(1)).deleteExam(1, 1);

        for (SimulatedArtemisStudent simulatedStudent : List.of(
            simulatedArtemisStudent1,
            simulatedArtemisStudent2,
            simulatedArtemisStudent3
        )) {
            verify(simulatedStudent, times(1)).login();
            verify(simulatedStudent, times(1)).performInitialCalls();
            verify(simulatedStudent, times(1)).startExamParticipation(1, 1, 0);
            verify(simulatedStudent, times(1)).participateInExam(1, 1);
            verify(simulatedStudent, times(1)).submitAndEndExam(1, 1);
        }

        verify(simulationWebsocketService, times(2)).sendRunStatusUpdate(run);
        assertEquals(RUNNING, statusesOnWebsocketUpdate.get(0));
        assertEquals(FINISHED, statusesOnWebsocketUpdate.get(1));

        verify(simulationWebsocketService, times(1)).sendSimulationResult(run);
        verify(simulationWebsocketService, times(1)).sendRunLogMessage(eq(run), argThat(LogMessage::isError));

        verify(simulationResultService, times(1)).calculateAndSaveResult(eq(run), any());

        mockedSimulatedArtemisUser.verify(() -> createArtemisAdminFromUser("", adminUser, artemisUserService), times(1));

        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser1, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser2, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
        mockedSimulatedArtemisUser.verify(
            () -> createArtemisStudent("", studentUser3, artemisUserService, 8, 15, ArtemisAuthMechanism.ONLINE_IDE),
            times(1)
        );
    }
}
