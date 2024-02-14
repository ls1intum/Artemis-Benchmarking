package de.tum.cit.ase.web.websocket;

import de.tum.cit.ase.domain.CiStatus;
import de.tum.cit.ase.domain.LogMessage;
import de.tum.cit.ase.domain.SimulationRun;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Service
public class SimulationWebsocketService {

    private static final String TOPIC_SIMULATION_RESULT = "/topic/simulation/runs/%d/result";
    private static final String TOPIC_RUN_STATUS_UPDATE = "/topic/simulation/runs/%d/status";
    private static final String TOPIC_RUN_LOG_MESSAGE = "/topic/simulation/runs/%d/log";
    private static final String TOPIC_NEW_RUN = "/topic/simulation/%d/runs/new";
    private static final String TOPIC_RUN_CI_UPDATE = "/topic/simulation/runs/%d/ci-status";

    private final SimpMessageSendingOperations messagingTemplate;

    public SimulationWebsocketService(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Send the simulation result (= the list of stats) to all clients subscribed to the corresponding topic.
     * @param run the simulation run that was completed
     */
    public void sendSimulationResult(SimulationRun run) {
        messagingTemplate.convertAndSend(String.format(TOPIC_SIMULATION_RESULT, run.getId()), run.getStats());
    }

    /**
     * Send the simulation status to all clients subscribed to the corresponding topic.
     * @param run the simulation run whose status was updated
     */
    public void sendRunStatusUpdate(SimulationRun run) {
        messagingTemplate.convertAndSend(String.format(TOPIC_RUN_STATUS_UPDATE, run.getId()), run.getStatus());
    }

    /**
     * Send a log message to all clients subscribed to the corresponding topic.
     * @param run the simulation run to which the message belongs
     * @param logMessage the log message to send
     */
    public void sendRunLogMessage(SimulationRun run, LogMessage logMessage) {
        messagingTemplate.convertAndSend(String.format(TOPIC_RUN_LOG_MESSAGE, run.getId()), logMessage);
    }

    public void sendNewRun(SimulationRun run) {
        messagingTemplate.convertAndSend(String.format(TOPIC_NEW_RUN, run.getSimulation().getId()), run);
    }

    public void sendRunCiUpdate(long runId, CiStatus status) {
        messagingTemplate.convertAndSend(String.format(TOPIC_RUN_CI_UPDATE, runId), status);
    }
}
