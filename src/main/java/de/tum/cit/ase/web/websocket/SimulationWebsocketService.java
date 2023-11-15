package de.tum.cit.ase.web.websocket;

import de.tum.cit.ase.domain.LogMessage;
import de.tum.cit.ase.domain.SimulationRun;
import de.tum.cit.ase.domain.SimulationStats;
import java.util.Set;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Service
public class SimulationWebsocketService {

    private static final String TOPIC_SIMULATION_RESULT = "/topic/simulation/runs/%d/result";
    private static final String TOPIC_RUN_STATUS_UPDATE = "/topic/simulation/runs/%d/status";
    private static final String TOPIC_RUN_LOG_MESSAGE = "/topic/simulation/runs/%d/log";

    private final SimpMessageSendingOperations messagingTemplate;

    public SimulationWebsocketService(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendSimulationResult(SimulationRun run) {
        messagingTemplate.convertAndSend(String.format(TOPIC_SIMULATION_RESULT, run.getId()), run.getStats());
    }

    public void sendRunStatusUpdate(SimulationRun run) {
        messagingTemplate.convertAndSend(String.format(TOPIC_RUN_STATUS_UPDATE, run.getId()), run.getStatus());
    }

    public void sendRunLogMessage(SimulationRun run, LogMessage logMessage) {
        messagingTemplate.convertAndSend(String.format(TOPIC_RUN_LOG_MESSAGE, run.getId()), logMessage);
    }
}
