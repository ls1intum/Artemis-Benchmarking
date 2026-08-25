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
import org.springframework.stereotype.Service;

/**
 * Service for managing the Artemis CI status.
 */
@Service
public class CiStatusService {

    private final Logger log = LoggerFactory.getLogger(CiStatusService.class);

    /** How often the number of outstanding build jobs is re-read from Artemis. */
    private static final long POLL_INTERVAL_MS = 1000L * 60;

    /**
     * Polls without the outstanding count decreasing before tracking gives up.
     * <p>
     * A saturated queue does stall legitimately, so this cannot be 1, but 15 was far too generous in practice: the
     * caller blocks on this loop, so every minute spent waiting on a queue that will never drain is a minute the whole
     * simulation queue is frozen and the next run sits in QUEUED. Fifteen minutes of that is long enough to look like
     * a hang and provoke a restart, which is how it was actually experienced.
     * <p>
     * Five still tolerates a genuinely slow queue: with six concurrent build slots at roughly 13 seconds a job, five
     * minutes of no completions at all means the agents are not working, not that they are busy.
     */
    private static final int MAX_POLLS_WITHOUT_PROGRESS = 5;
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
        status.setStartTimeNanos(System.nanoTime());
        return ciStatusRepository.save(status);
    }

    /**
     * Get the CiStatus for a given SimulationRun.
     *
     * @param simulationRun the SimulationRun to get the CiStatus for
     * @return the CiStatus for the given SimulationRun
     */
    public CiStatus getCiStatusForSimulationRun(SimulationRun simulationRun) {
        return ciStatusRepository.findBySimulationRunId(simulationRun.getId());
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
            CiStatus status = getCiStatusForSimulationRun(simulationRun);

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

            int numberOfQueuedJobs = submissions.size();
            log.info("Number of already existing results {}", getNumberOfResults(submissions));
            status.setTotalJobs(numberOfQueuedJobs);
            status.setQueuedJobs(numberOfQueuedJobs);
            status = ciStatusRepository.save(status);
            websocketService.sendRunCiUpdate(simulationRun.getId(), status);

            int previousQueuedJobs = numberOfQueuedJobs;
            int pollsWithoutProgress = 0;

            while (numberOfQueuedJobs > 0) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    // Leave rather than loop on: an interrupted sleep returns immediately, so continuing here would
                    // spin at full speed against Artemis.
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while tracking CI status for simulation run {}; stopping", simulationRun.getId());
                    break;
                }
                log.info("Updating CI status for simulation run {}", simulationRun.getId());

                submissions = new ArrayList<>();
                for (var participation : participations) {
                    submissions.addAll(admin.getSubmissions(participation.getId()));
                }
                numberOfQueuedJobs = submissions.size() - getNumberOfResults(submissions);
                log.info("Currently queued buildjobs: {}", numberOfQueuedJobs);

                status.setQueuedJobs(numberOfQueuedJobs);
                status.setTimeInMinutes(getTimeElapsedInMinutes(status.getStartTimeNanos()));
                status.setAvgJobsPerMinute((double) (status.getTotalJobs() - status.getQueuedJobs()) / status.getTimeInMinutes());
                status = ciStatusRepository.save(status);
                websocketService.sendRunCiUpdate(simulationRun.getId(), status);

                // A build job that never produces a result leaves the count stuck above zero for good. Cancelled jobs
                // are the common case, but a dead agent or an exercise whose image cannot be pulled does the same.
                // Without this the loop never ends, and because the caller blocks on it the whole simulation queue
                // stops: every later run sits in QUEUED forever.
                pollsWithoutProgress = numberOfQueuedJobs < previousQueuedJobs ? 0 : pollsWithoutProgress + 1;
                previousQueuedJobs = numberOfQueuedJobs;

                if (pollsWithoutProgress >= MAX_POLLS_WITHOUT_PROGRESS) {
                    log.warn(
                        "CI status for simulation run {} stopped making progress: {} build jobs still without a result after {} minutes without change. " +
                            "Giving up rather than blocking the simulation queue. Were the build jobs cancelled, or are the build agents unable to run them?",
                        simulationRun.getId(),
                        numberOfQueuedJobs,
                        MAX_POLLS_WITHOUT_PROGRESS
                    );
                    break;
                }
            }

            status.setFinished(true);
            status.setTimeInMinutes(getTimeElapsedInMinutes(status.getStartTimeNanos()));
            status = ciStatusRepository.save(status);
            websocketService.sendRunCiUpdate(simulationRun.getId(), status);
            log.info(
                "Finished subscribing to CI status for simulation run {} after {} minutes",
                simulationRun.getId(),
                status.getTimeInMinutes()
            );

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

    private long getTimeElapsedInMinutes(long startTimeNanos) {
        return (System.nanoTime() - startTimeNanos) / (1000L * 1000 * 1000 * 60);
    }
}
