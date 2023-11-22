package de.tum.cit.ase.service.artemis;

import de.tum.cit.ase.domain.ArtemisUser;
import de.tum.cit.ase.repository.ArtemisUserRepository;
import de.tum.cit.ase.service.dto.ArtemisUserForCreationDTO;
import de.tum.cit.ase.service.dto.ArtemisUserPatternDTO;
import de.tum.cit.ase.util.ArtemisServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ArtemisUserService {

    private final Logger log = LoggerFactory.getLogger(ArtemisUserService.class);
    private final ArtemisUserRepository artemisUserRepository;

    public ArtemisUserService(ArtemisUserRepository artemisUserRepository) {
        this.artemisUserRepository = artemisUserRepository;
    }

    public List<ArtemisUser> createArtemisUsersByPattern(ArtemisServer server, ArtemisUserPatternDTO pattern) {
        if (pattern.getFrom() >= pattern.getTo() || pattern.getFrom() < 0) {
            throw new IllegalArgumentException("from must be smaller than to and greater than 0");
        } else if (!pattern.getUsernamePattern().contains("{i}") || !pattern.getPasswordPattern().contains("{i}")) {
            throw new IllegalArgumentException("usernamePattern and passwordPattern must contain {i} as placeholder for the index");
        } else if (server == null) {
            throw new IllegalArgumentException("server must not be null");
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
            } catch (DuplicatedArtemisUserException e) {
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

        artemisUser.setServerWideId(
            Objects.requireNonNullElseGet(artemisUserDTO.getServerWideId(), () -> findLowestFreeServerWideId(server))
        );

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

    private ArtemisUser saveArtemisUser(ArtemisUser artemisUser) {
        if (
            artemisUserRepository
                .findAllByServer(artemisUser.getServer())
                .stream()
                .anyMatch(user -> user.getServerWideId() == artemisUser.getServerWideId())
        ) {
            throw new DuplicatedArtemisUserException(artemisUser);
        }
        return artemisUserRepository.save(artemisUser);
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
        int n = numbers.size();
        for (int i = 0; i < n; i++) {
            while (numbers.get(i) > 0 && numbers.get(i) <= n && numbers.get(numbers.get(i) - 1) != numbers.get(i)) {
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
