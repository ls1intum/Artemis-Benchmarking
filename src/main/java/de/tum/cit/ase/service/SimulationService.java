package de.tum.cit.ase.service;

import de.tum.cit.ase.service.util.Simulation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    @Value("${artemis.local.username_template}")
    private String testUserUsernameTemplate;

    @Value("${artemis.local.password_template}")
    private String testUserPasswordTemplate;

    public void simulate() {
        Simulation simulation = new Simulation(10, testUserPasswordTemplate, testUserUsernameTemplate);
        simulation.simulateExam();
    }
}
