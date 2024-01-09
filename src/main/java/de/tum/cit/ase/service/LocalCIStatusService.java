package de.tum.cit.ase.service;

import de.tum.cit.ase.domain.LocalCIStatus;
import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.repository.LocalCIStatusRepository;
import de.tum.cit.ase.service.artemis.interaction.SimulatedArtemisAdmin;
import de.tum.cit.ase.web.websocket.SimulationWebsocketService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class LocalCIStatusService {

    private final LocalCIStatusRepository localCIStatusRepository;
    private final SimulationWebsocketService websocketService;

    public LocalCIStatusService(LocalCIStatusRepository localCIStatusRepository, SimulationWebsocketService websocketService) {
        this.localCIStatusRepository = localCIStatusRepository;
        this.websocketService = websocketService;
    }

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
        LocalCIStatus status = createLocalCIStatus(simulationRun);

        int numberOfQueuedJobs = admin.getBuildQueue(courseId).size();
        status.setTotalJobs(numberOfQueuedJobs);
        status.setQueuedJobs(numberOfQueuedJobs);

        do {
            try {
                Thread.sleep(1000 * 60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            numberOfQueuedJobs = admin.getBuildQueue(courseId).size();
            status.setQueuedJobs(numberOfQueuedJobs);
            status.setTimeInMinutes(status.getTimeInMinutes() + 1);
            status.setAvgJobsPerMinute((status.getTotalJobs() - status.getQueuedJobs()) / status.getTimeInMinutes());
            status = localCIStatusRepository.save(status);
            websocketService.sendRunLocalCIUpdate(simulationRun.getId(), status);
        } while (numberOfQueuedJobs > 0);
        status.setFinished(true);
        status = localCIStatusRepository.save(status);
        websocketService.sendRunLocalCIUpdate(simulationRun.getId(), status);
    }
}
