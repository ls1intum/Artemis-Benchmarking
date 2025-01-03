package de.tum.cit.aet.service;

import de.tum.cit.aet.artemisModel.DomainObject;
import de.tum.cit.aet.artemisModel.Participation;
import de.tum.cit.aet.artemisModel.ProgrammingExercise;
import de.tum.cit.aet.artemisModel.Submission;
import de.tum.cit.aet.domain.CiStatus;
import de.tum.cit.aet.domain.SimulationRun;
import de.tum.cit.aet.repository.CiStatusRepository;
import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisAdmin;
import de.tum.cit.aet.web.websocket.SimulationWebsocketService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for managing the Artemis CI status.
 */
@Service
public class CiStatusService {

    private final Logger log = LoggerFactory.getLogger(CiStatusService.class);
    private final CiStatusRepository ciStatusRepository;
    private final SimulationWebsocketService websocketService;

    public CiStatusService(CiStatusRepository ciStatusRepository, SimulationWebsocketService websocketService) {
        this.ciStatusRepository = ciStatusRepository;
        this.websocketService = websocketService;
        cleanup();
    }

    /**
     * Create a new CiStatus for a given SimulationRun.
     *
     * @param simulationRun the SimulationRun to create the CiStatus for
     * @return the created CiStatus
     */
    public CiStatus createCiStatus(SimulationRun simulationRun) {
        CiStatus status = new CiStatus();
        status.setSimulationRun(simulationRun);
        status.setFinished(false);
        status.setAvgJobsPerMinute(0);
        status.setQueuedJobs(0);
        status.setTotalJobs(0);
        status.setTimeInMinutes(0);
        return ciStatusRepository.save(status);
    }

    /**
     * Subscribe to the CI status for a given SimulationRun.
     * This method will update the status of the SimulationRun in the database and send updates to the clients via WebSockets.
     * It gets the status through the build queue.
     *
     * @param simulationRun the SimulationRun to subscribe to
     * @param admin the SimulatedArtemisAdmin to use for querying the CI status
     * @param courseId the ID of the course to use for querying the CI status
     */
    @Async
    public void subscribeToCiStatusViaBuildQueue(SimulationRun simulationRun, SimulatedArtemisAdmin admin, long courseId) {
        log.info("Subscribing to CI status for simulation run {}", simulationRun.getId());
        CiStatus status = createCiStatus(simulationRun);

        int numberOfQueuedJobs = admin.getBuildQueue(courseId).size();
        status.setTotalJobs(numberOfQueuedJobs);
        status.setQueuedJobs(numberOfQueuedJobs);
        status = ciStatusRepository.save(status);
        websocketService.sendRunCiUpdate(simulationRun.getId(), status);

        do {
            try {
                Thread.sleep(1000 * 60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.debug("Updating CI status for simulation run {}", simulationRun.getId());
            numberOfQueuedJobs = admin.getBuildQueue(courseId).size();
            status.setQueuedJobs(numberOfQueuedJobs);
            status.setTimeInMinutes(status.getTimeInMinutes() + 1);
            status.setAvgJobsPerMinute((double) (status.getTotalJobs() - status.getQueuedJobs()) / status.getTimeInMinutes());
            status = ciStatusRepository.save(status);
            websocketService.sendRunCiUpdate(simulationRun.getId(), status);
        } while (numberOfQueuedJobs > 0);
        status.setFinished(true);
        status = ciStatusRepository.save(status);
        websocketService.sendRunCiUpdate(simulationRun.getId(), status);
        log.info("Finished subscribing to CI status for simulation run {}", simulationRun.getId());
    }

    /**
     * Subscribe to the CI status for a given SimulationRun.
     * This method will update the status of the SimulationRun in the database and send updates to the clients via WebSockets.
     * It gets the status through the results of the submissions.
     *
     * @param simulationRun the SimulationRun to subscribe to
     * @param admin the SimulatedArtemisAdmin to use for querying the CI status
     * @param examId the ID of the exam to use for querying the CI status
     * @return a CompletableFuture that will be completed when the subscription is finished
     */
    public CompletableFuture<Void> subscribeToCiStatusViaResults(SimulationRun simulationRun, SimulatedArtemisAdmin admin, long examId) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Subscribing to CI status for simulation run {}", simulationRun.getId());
            CiStatus status = createCiStatus(simulationRun);

            List<Long> programmingExerciseIds = admin
                .getExamWithExercises(examId)
                .getExerciseGroups()
                .stream()
                .flatMap(exerciseGroup -> exerciseGroup.getExercises().stream())
                .filter(exercise -> exercise instanceof ProgrammingExercise)
                .map(DomainObject::getId)
                .toList();

            List<Submission> submissions = new ArrayList<>();
            List<Participation> participations = new ArrayList<>();
            for (Long programmingExerciseId : programmingExerciseIds) {
                participations.addAll(admin.getParticipations(programmingExerciseId));
            }
            for (var participation : participations) {
                submissions.addAll(admin.getSubmissions(participation.getId()));
            }

            int numberOfQueuedJobs = submissions.size() - getNumberOfResults(submissions);
            status.setTotalJobs(numberOfQueuedJobs);
            status.setQueuedJobs(numberOfQueuedJobs);
            status = ciStatusRepository.save(status);
            websocketService.sendRunCiUpdate(simulationRun.getId(), status);

            do {
                try {
                    Thread.sleep(1000 * 60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("Updating CI status for simulation run {}", simulationRun.getId());

                submissions = new ArrayList<>();
                for (var participation : participations) {
                    submissions.addAll(admin.getSubmissions(participation.getId()));
                }
                numberOfQueuedJobs = submissions.size() - getNumberOfResults(submissions);
                log.info("Currently queued buildjobs: {}", numberOfQueuedJobs);

                status.setQueuedJobs(numberOfQueuedJobs);
                status.setTimeInMinutes(status.getTimeInMinutes() + 1);
                status.setAvgJobsPerMinute((double) (status.getTotalJobs() - status.getQueuedJobs()) / status.getTimeInMinutes());
                status = ciStatusRepository.save(status);
                websocketService.sendRunCiUpdate(simulationRun.getId(), status);
            } while (numberOfQueuedJobs > 0);

            status.setFinished(true);
            status = ciStatusRepository.save(status);
            websocketService.sendRunCiUpdate(simulationRun.getId(), status);
            log.info("Finished subscribing to CI status for simulation run {}", simulationRun.getId());

            return null;
        });
    }

    private int getNumberOfResults(List<Submission> submissions) {
        return submissions
            .stream()
            .filter(submission -> submission.getResults() != null)
            .flatMap(submission -> submission.getResults().stream())
            .toList()
            .size();
    }

    /**
     * Delete all CiStatus entities that are not finished.
     */
    private void cleanup() {
        log.info("Cleaning up CI status");
        ciStatusRepository.deleteAllNotFinished();
    }
}
