package de.tum.cit.ase.service;

import de.tum.cit.ase.domain.CiStatus;
import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.repository.CiStatusRepository;
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

    @Async
    public void subscribeToCiStatus(SimulationRun simulationRun, SimulatedArtemisAdmin admin, long courseId) {
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
     * Delete all CiStatus entities that are not finished.
     */
    private void cleanup() {
        log.info("Cleaning up CI status");
        ciStatusRepository.deleteAllNotFinished();
    }
}
