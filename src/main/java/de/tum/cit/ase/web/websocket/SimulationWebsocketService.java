package de.tum.cit.ase.web.websocket;

import de.tum.cit.ase.service.util.SimulationResult;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Service
public class SimulationWebsocketService {

    private final SimpMessageSendingOperations messagingTemplate;

    public SimulationWebsocketService(SimpMessageSendingOperations messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendSimulationResult(SimulationResult simulationResult) {
        messagingTemplate.convertAndSend("/topic/simulation/completed", simulationResult);
    }
}