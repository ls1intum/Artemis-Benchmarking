package de.tum.cit.aet.service.artemis;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import de.tum.cit.aet.domain.ArtemisUser;
import de.tum.cit.aet.repository.ArtemisUserRepository;
import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisAdmin;
import de.tum.cit.aet.service.artemis.interaction.SimulatedArtemisUser;
import de.tum.cit.aet.service.dto.ArtemisUserForCreationDTO;
import de.tum.cit.aet.service.dto.ArtemisUserPatternDTO;
import de.tum.cit.aet.util.ArtemisServer;
import de.tum.cit.aet.util.NumberRangeParser;
import de.tum.cit.aet.util.SshUtils;
import de.tum.cit.aet.web.rest.errors.BadRequestAlertException;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for managing ArtemisUsers.
 */
@Service
public class ArtemisUserService {

    private final Logger log = LoggerFactory.getLogger(ArtemisUserService.class);
    private final ArtemisUserRepository artemisUserRepository;
    private final ArtemisConfiguration artemisConfiguration;

    public ArtemisUserService(ArtemisUserRepository artemisUserRepository, ArtemisConfiguration artemisConfiguration) {
        this.artemisUserRepository = artemisUserRepository;
        this.artemisConfiguration = artemisConfiguration;
    }

    /**
     * Creates a list of ArtemisUsers from a pattern.
     * If createOnArtemis is true, the users will also be created on the Artemis server.
     *
     * @param server the ArtemisServer to create the users for
     * @param pattern the pattern to use for the usernames and passwords
     * @return a list of the created ArtemisUsers
     */
    public List<ArtemisUser> createArtemisUsersByPattern(ArtemisServer server, ArtemisUserPatternDTO pattern) {
        log.info("Creating ArtemisUsers by pattern for {}", server);
        if (pattern.getFrom() >= pattern.getTo() || pattern.getFrom() <= 0) {
            throw new BadRequestAlertException("from must be smaller than to and greater than 0", "artemisUser", "invalidRange");
        } else if (!pattern.getUsernamePattern().contains("{i}")) {
            throw new BadRequestAlertException(
                "usernamePattern must contain {i} as placeholder for the index",
                "artemisUser",
                "missingPlaceholder"
            );
        } else if (server == null) {
            throw new BadRequestAlertException("server must not be null", "artemisUser", "missingServer");
        }

        SimulatedArtemisAdmin simulatedArtemisAdmin = null;
        if (pattern.isCreateOnArtemis()) {
            ArtemisUser admin = getAdminUser(server);
            if (admin == null) {
                throw new BadRequestAlertException("No admin user found for server", "artemisUser", "missingAdmin");
            }
            simulatedArtemisAdmin = SimulatedArtemisUser.createArtemisAdminFromCredentials(
                artemisConfiguration.getUrl(server),
                admin.getUsername(),
                admin.getPassword()
            );
            simulatedArtemisAdmin.login();
        }
        log.info("Generate SSH keys... this might take some time");
        AtomicInteger sshKeyCounter = new AtomicInteger(0);
        int totalKeys = pattern.getTo() - pattern.getFrom();
        Pair<String, String>[] pregeneratedSSHkeys = new Pair[totalKeys + 1];

        IntStream.range(pattern.getFrom(), pattern.getTo() + 1)
            .parallel()
            .forEach(i -> {
                if (sshKeyCounter.get() % 100 == 0) {
                    log.info("{{}} of {{}} keys created...", sshKeyCounter.get(), totalKeys);
                }
                pregeneratedSSHkeys[i - pattern.getFrom()] = SshUtils.generateSshKeyPair();
                sshKeyCounter.getAndIncrement();
            });
        log.info("Done generating {{}} SSH keys", totalKeys);

        List<ArtemisUser> createdUsers = new ArrayList<>();
        for (int i = pattern.getFrom(); i < pattern.getTo(); i++) {
            ArtemisUser artemisUser = new ArtemisUser();
            artemisUser.setServer(server);
            artemisUser.setServerWideId(i);
            var username = pattern.getUsernamePattern().replace("{i}", String.valueOf(i));
            var password = pattern.getPasswordPattern().replace("{i}", String.valueOf(i));
            artemisUser.setUsername(username);
            artemisUser.setPassword(password);
            artemisUser.setKeyPair(pregeneratedSSHkeys[i - pattern.getFrom()]);

            try {
                ArtemisUser createdUser = saveArtemisUser(artemisUser);
                // Create user on Artemis if necessary
                if (pattern.isCreateOnArtemis() && simulatedArtemisAdmin != null) {
                    var firstName = pattern.getFirstNamePattern().replace("{i}", String.valueOf(i));
                    var lastName = pattern.getLastNamePattern().replace("{i}", String.valueOf(i));
                    var email = pattern.getEmailPattern().replace("{i}", String.valueOf(i));
                    simulatedArtemisAdmin.createUser(username, password, firstName, lastName, email);
                }
                // The order of operations is important here, as the user might not be created on Artemis if an exception is thrown
                createdUsers.add(createdUser);
            } catch (BadRequestAlertException e) {
                log.debug(e.getMessage() + ". Skipping user.");
            }
        }
        log.info("Created {} ArtemisUsers by pattern", createdUsers.size());
        return createdUsers;
    }

    /**
     * Creates a new ArtemisUser.
     *
     * @param server the ArtemisServer to create the user for
     * @param artemisUserDTO the DTO containing the username, password and server-wide ID of the user
     * @return the created ArtemisUser
     * @throws BadRequestAlertException if the server-wide ID is already taken, negative or the username or password is invalid
     */
    public ArtemisUser createArtemisUser(ArtemisServer server, ArtemisUserForCreationDTO artemisUserDTO) {
        log.info("Creating ArtemisUser for {}", server);
        ArtemisUser artemisUser = new ArtemisUser();
        artemisUser.setServer(server);
        artemisUser.setUsername(artemisUserDTO.getUsername());
        artemisUser.setPassword(artemisUserDTO.getPassword());
        artemisUser.setKeyPair(SshUtils.generateSshKeyPair());

        if (artemisUserDTO.getServerWideId() != null) {
            artemisUser.setServerWideId(artemisUserDTO.getServerWideId());
        } else {
            artemisUser.setServerWideId(findLowestFreeServerWideId(server));
        }

        return saveArtemisUser(artemisUser);
    }

    /**
     * Get an ArtemisUser by its ID.
     *
     * @param id the ID of the ArtemisUser to get
     * @return the ArtemisUser
     * @throws NoSuchElementException if the user does not exist
     */
    public ArtemisUser getArtemisUser(long id) {
        return artemisUserRepository.findById(id).orElseThrow();
    }

    /**
     * Get all ArtemisUsers for a given ArtemisServer.
     *
     * @param server the ArtemisServer to get the users for
     * @return a list of all ArtemisUsers
     */
    public List<ArtemisUser> getArtemisUsersByServer(ArtemisServer server) {
        return artemisUserRepository.findAllByServer(server);
    }

    /**
     * Deletes an ArtemisUser by its ID.
     *
     * @param id the ID of the ArtemisUser to delete
     */
    public void deleteArtemisUser(long id) {
        artemisUserRepository.deleteById(id);
    }

    /**
     * Deletes all ArtemisUsers for a given ArtemisServer.
     *
     * @param server the ArtemisServer to delete the users for
     */
    public void deleteByServer(ArtemisServer server) {
        log.info("Deleting all ArtemisUsers for {}", server);
        artemisUserRepository.deleteByServer(server);
    }

    /**
     * Creates a list of ArtemisUsers from a CSV file.
     *
     * @param file the CSV file to read the users from
     * @param server the ArtemisServer to create the users for
     * @return a list of the created ArtemisUsers
     */
    public List<ArtemisUser> createArtemisUsersFromCSV(MultipartFile file, ArtemisServer server) {
        log.info("Creating ArtemisUsers from CSV for {}", server);
        List<ArtemisUserForCreationDTO> artemisUserDTOs;
        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            CsvToBean<ArtemisUserForCreationDTO> cb = new CsvToBeanBuilder<ArtemisUserForCreationDTO>(reader)
                .withType(ArtemisUserForCreationDTO.class)
                .withIgnoreLeadingWhiteSpace(true)
                .build();

            artemisUserDTOs = cb.parse();
        } catch (Exception e) {
            throw new BadRequestAlertException("Could not read CSV file", "artemisUser", "csvReadError");
        }
        List<ArtemisUser> result = new LinkedList<>();
        for (ArtemisUserForCreationDTO artemisUserDTO : artemisUserDTOs) {
            if (artemisUserDTO.getServerWideId() != null && artemisUserDTO.getServerWideId() == 0) {
                continue;
            }
            try {
                result.add(createArtemisUser(server, artemisUserDTO));
            } catch (BadRequestAlertException e) {
                log.debug(e.getMessage() + ". Skipping user.");
            }
        }
        log.info("Created {} ArtemisUsers from CSV", result.size());
        return result;
    }

    /**
     * Validates and saves an ArtemisUser.
     *
     * @param artemisUser the ArtemisUser to save
     * @return the saved ArtemisUser
     * @throws BadRequestAlertException if the server-wide ID is already taken, negative or the username or password is invalid
     */
    private ArtemisUser saveArtemisUser(ArtemisUser artemisUser) {
        if (
            artemisUserRepository
                .findAllByServer(artemisUser.getServer())
                .stream()
                .anyMatch(user -> user.getServerWideId() == artemisUser.getServerWideId())
        ) {
            throw new BadRequestAlertException("User with server-wide ID already exists", "artemisUser", "duplicatedServerWideId");
        } else if (artemisUser.getServerWideId() < 0) {
            throw new BadRequestAlertException("Server-wide ID must be positive", "artemisUser", "negativeServerWideId");
        } else if (artemisUser.getUsername() == null || artemisUser.getUsername().isBlank()) {
            throw new BadRequestAlertException("Username must not be empty", "artemisUser", "emptyUsername");
        } else if (artemisUser.getPassword() == null || artemisUser.getPassword().isBlank()) {
            throw new BadRequestAlertException("Password must not be empty", "artemisUser", "emptyPassword");
        } else if (
            artemisUserRepository
                .findAllByServer(artemisUser.getServer())
                .stream()
                .anyMatch(user -> user.getUsername().equals(artemisUser.getUsername()))
        ) {
            throw new BadRequestAlertException("User with username already exists", "artemisUser", "duplicatedUsername");
        }

        return artemisUserRepository.save(artemisUser);
    }

    /**
     * Updates an ArtemisUser.
     *
     * @param id the ID of the ArtemisUser to update
     * @param artemisUser the updated ArtemisUser
     * @return the updated ArtemisUser
     */
    public ArtemisUser updateArtemisUser(Long id, ArtemisUser artemisUser) {
        log.info("Updating ArtemisUser with ID {}", id);
        if (!Objects.equals(id, artemisUser.getId())) {
            throw new IllegalArgumentException("Id in path and body do not match!");
        }

        ArtemisUser existingUser = artemisUserRepository.findById(id).orElseThrow();
        if (existingUser.getServerWideId() != artemisUser.getServerWideId()) {
            throw new IllegalArgumentException("Server-wide ID cannot be changed!");
        } else if (existingUser.getServer() != artemisUser.getServer()) {
            throw new IllegalArgumentException("Server cannot be changed!");
        } else if (artemisUser.getUsername() == null || artemisUser.getUsername().isBlank()) {
            throw new BadRequestAlertException("Username must not be empty", "artemisUser", "emptyUsername");
        } else if (artemisUser.getPassword() == null || artemisUser.getPassword().isBlank()) {
            throw new BadRequestAlertException("Password must not be empty", "artemisUser", "emptyPassword");
        } else if (
            artemisUserRepository
                .findAllByServer(artemisUser.getServer())
                .stream()
                .anyMatch(user -> user.getUsername().equals(artemisUser.getUsername()) && !user.getId().equals(id))
        ) {
            throw new BadRequestAlertException("User with username already exists", "artemisUser", "duplicatedUsername");
        }
        return artemisUserRepository.save(artemisUser);
    }

    /**
     * Get a list of ArtemisUsers from a range of server-wide IDs.
     *
     * @param server the ArtemisServer to get the users for
     * @param range the range of server-wide IDs to get the users for
     * @return a list of the ArtemisUsers
     */
    public List<ArtemisUser> getUsersFromRange(ArtemisServer server, String range) {
        List<ArtemisUser> users = new ArrayList<>();
        List<Integer> serverWideIds = NumberRangeParser.parseNumberRange(range);
        for (Integer serverWideId : serverWideIds) {
            var user = artemisUserRepository.findByServerAndServerWideId(server, serverWideId);
            if (user != null) {
                users.add(user);
            }
        }
        return users;
    }

    /**
     * Get the admin user for a given ArtemisServer.
     *
     * @param server the ArtemisServer to get the admin user for
     * @return the admin user
     */
    public ArtemisUser getAdminUser(ArtemisServer server) {
        if (server == ArtemisServer.PRODUCTION) {
            throw new IllegalArgumentException("Cannot get admin user for production server!");
        }
        return artemisUserRepository.findByServerAndServerWideId(server, 0);
    }

    /**
     * Finds the lowest free server-wide ID for a given ArtemisServer.
     *
     * @param server the ArtemisServer to find the lowest free server-wide ID for
     * @return the lowest free server-wide ID
     */
    private int findLowestFreeServerWideId(ArtemisServer server) {
        List<ArtemisUser> artemisUsers = artemisUserRepository.findAllByServer(server);
        return findLowestMissingPositive(artemisUsers.stream().map(ArtemisUser::getServerWideId).toList());
    }

    /**
     * Finds the lowest missing positive number in a list of numbers in O(n) time and O(1) space.
     *
     * @param numbers List of unique integers, may contain negative numbers
     * @return The lowest missing positive number
     */
    public static int findLowestMissingPositive(List<Integer> numbers) {
        numbers = new LinkedList<>(numbers); // Copy list to avoid modifying the original list
        int n = numbers.size();
        for (int i = 0; i < n; i++) {
            while (numbers.get(i) > 0 && numbers.get(i) <= n && !Objects.equals(numbers.get(numbers.get(i) - 1), numbers.get(i))) {
                int correctIndex = numbers.get(i) - 1;
                int temp = numbers.get(i);
                numbers.set(i, numbers.get(correctIndex));
                numbers.set(correctIndex, temp);
            }
        }

        for (int i = 0; i < n; i++) {
            if (numbers.get(i) != i + 1) {
                return i + 1;
            }
        }

        return n + 1;
    }

    /**
     * Generates a new SSH key pair for the given ArtemisUser.
     *
     * @param artemisUser the ArtemisUser to generate the key pair for
     * @return the ArtemisUser with the generated key pair
     */
    public ArtemisUser generateKeyPair(ArtemisUser artemisUser) {
        artemisUser.setKeyPair(SshUtils.generateSshKeyPair());
        return artemisUserRepository.save(artemisUser);
    }
}
