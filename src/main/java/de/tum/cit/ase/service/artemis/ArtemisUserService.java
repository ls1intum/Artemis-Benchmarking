package de.tum.cit.ase.service.artemis;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import de.tum.cit.ase.domain.ArtemisUser;
import de.tum.cit.ase.repository.ArtemisUserRepository;
import de.tum.cit.ase.service.dto.ArtemisUserForCreationDTO;
import de.tum.cit.ase.service.dto.ArtemisUserPatternDTO;
import de.tum.cit.ase.util.ArtemisServer;
import de.tum.cit.ase.util.NumberRangeParser;
import de.tum.cit.ase.web.rest.errors.BadRequestAlertException;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ArtemisUserService {

    private final Logger log = LoggerFactory.getLogger(ArtemisUserService.class);
    private final ArtemisUserRepository artemisUserRepository;

    public ArtemisUserService(ArtemisUserRepository artemisUserRepository) {
        this.artemisUserRepository = artemisUserRepository;
    }

    public List<ArtemisUser> createArtemisUsersByPattern(ArtemisServer server, ArtemisUserPatternDTO pattern) {
        if (pattern.getFrom() >= pattern.getTo() || pattern.getFrom() <= 0) {
            throw new BadRequestAlertException("from must be smaller than to and greater than 0", "artemisUser", "invalidRange");
        } else if (!pattern.getUsernamePattern().contains("{i}") || !pattern.getPasswordPattern().contains("{i}")) {
            throw new BadRequestAlertException(
                "usernamePattern and passwordPattern must contain {i} as placeholder for the index",
                "artemisUser",
                "missingPlaceholder"
            );
        } else if (server == null) {
            throw new BadRequestAlertException("server must not be null", "artemisUser", "missingServer");
        }

        List<ArtemisUser> createdUsers = new ArrayList<>();
        for (int i = pattern.getFrom(); i < pattern.getTo(); i++) {
            ArtemisUser artemisUser = new ArtemisUser();
            artemisUser.setServer(server);
            artemisUser.setServerWideId(i);
            artemisUser.setUsername(pattern.getUsernamePattern().replace("{i}", String.valueOf(i)));
            artemisUser.setPassword(pattern.getPasswordPattern().replace("{i}", String.valueOf(i)));
            try {
                createdUsers.add(saveArtemisUser(artemisUser));
            } catch (BadRequestAlertException e) {
                log.warn(e.getMessage() + ". Skipping user.");
            }
        }
        return createdUsers;
    }

    public ArtemisUser createArtemisUser(ArtemisServer server, ArtemisUserForCreationDTO artemisUserDTO) {
        ArtemisUser artemisUser = new ArtemisUser();
        artemisUser.setServer(server);
        artemisUser.setUsername(artemisUserDTO.getUsername());
        artemisUser.setPassword(artemisUserDTO.getPassword());

        if (artemisUserDTO.getServerWideId() != null) {
            artemisUser.setServerWideId(artemisUserDTO.getServerWideId());
        } else {
            artemisUser.setServerWideId(findLowestFreeServerWideId(server));
        }

        return saveArtemisUser(artemisUser);
    }

    public ArtemisUser getArtemisUser(long id) {
        return artemisUserRepository.findById(id).orElseThrow();
    }

    public List<ArtemisUser> getArtemisUsersByServer(ArtemisServer server) {
        return artemisUserRepository.findAllByServer(server);
    }

    public void deleteArtemisUser(long id) {
        artemisUserRepository.deleteById(id);
    }

    public void deleteByServer(ArtemisServer server) {
        artemisUserRepository.deleteByServer(server);
    }

    public List<ArtemisUser> createArtemisUsersFromCSV(MultipartFile file, ArtemisServer server) {
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
                log.warn(e.getMessage() + ". Skipping user.");
            }
        }
        return result;
    }

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

    public ArtemisUser updateArtemisUser(Long id, ArtemisUser artemisUser) {
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

    public ArtemisUser getAdminUser(ArtemisServer server) {
        if (server == ArtemisServer.PRODUCTION) {
            throw new IllegalArgumentException("Cannot get admin user for production server!");
        }
        return artemisUserRepository.findByServerAndServerWideId(server, 0);
    }

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
}
