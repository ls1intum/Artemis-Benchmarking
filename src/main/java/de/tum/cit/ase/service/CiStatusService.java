package de.tum.cit.ase.service;

import de.tum.cit.ase.domain.LocalCIStatus;
import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.repository.LocalCIStatusRepository;
import de.tum.cit.ase.service.artemis.interaction.SimulatedArtemisAdmin;
import de.tum.cit.ase.web.websocket.SimulationWebsocketService;
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
    private final LocalCIStatusRepository localCIStatusRepository;
    private final SimulationWebsocketService websocketService;

    public CiStatusService(LocalCIStatusRepository localCIStatusRepository, SimulationWebsocketService websocketService) {
        this.localCIStatusRepository = localCIStatusRepository;
        this.websocketService = websocketService;
        cleanup();
    }

    /**
     * Create a new LocalCIStatus for a given SimulationRun.
     *
     * @param simulationRun the SimulationRun to create the LocalCIStatus for
     * @return the created LocalCIStatus
     */
    public LocalCIStatus createLocalCIStatus(SimulationRun simulationRun) {
        LocalCIStatus status = new LocalCIStatus();
        status.setSimulationRun(simulationRun);
        status.setFinished(false);
        status.setAvgJobsPerMinute(0);
        status.setQueuedJobs(0);
        status.setTotalJobs(0);
        status.setTimeInMinutes(0);
        return localCIStatusRepository.save(status);
    }

    @Async
    public void subscribeToLocalCIStatus(SimulationRun simulationRun, SimulatedArtemisAdmin admin, long courseId) {
        log.info("Subscribing to local CI status for simulation run {}", simulationRun.getId());
        LocalCIStatus status = createLocalCIStatus(simulationRun);

        int numberOfQueuedJobs = admin.getBuildQueue(courseId).size();
        status.setTotalJobs(numberOfQueuedJobs);
        status.setQueuedJobs(numberOfQueuedJobs);
        status = localCIStatusRepository.save(status);
        websocketService.sendRunLocalCIUpdate(simulationRun.getId(), status);

        do {
            try {
                Thread.sleep(1000 * 60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.debug("Updating local CI status for simulation run {}", simulationRun.getId());
            numberOfQueuedJobs = admin.getBuildQueue(courseId).size();
            status.setQueuedJobs(numberOfQueuedJobs);
            status.setTimeInMinutes(status.getTimeInMinutes() + 1);
            status.setAvgJobsPerMinute((double) (status.getTotalJobs() - status.getQueuedJobs()) / status.getTimeInMinutes());
            status = localCIStatusRepository.save(status);
            websocketService.sendRunLocalCIUpdate(simulationRun.getId(), status);
        } while (numberOfQueuedJobs > 0);
        status.setFinished(true);
        status = localCIStatusRepository.save(status);
        websocketService.sendRunLocalCIUpdate(simulationRun.getId(), status);
        log.info("Finished subscribing to local CI status for simulation run {}", simulationRun.getId());
    }

    /**
     * Delete all CiIStatus entities that are not finished.
     */
    private void cleanup() {
        log.info("Cleaning up local CI status");
        localCIStatusRepository.deleteAllNotFinished();
    }
}
