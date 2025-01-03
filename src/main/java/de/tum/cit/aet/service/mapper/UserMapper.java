package de.tum.cit.aet.service.mapper;

import de.tum.cit.aet.domain.Authority;
import de.tum.cit.aet.domain.User;
import de.tum.cit.aet.service.dto.AdminUserDTO;
import de.tum.cit.aet.service.dto.UserDTO;
import java.util.*;
import java.util.stream.Collectors;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.stereotype.Service;

/**
 * Mapper for the entity {@link User} and its DTO called {@link UserDTO}.
 * <p>
 * Normal mappers are generated using MapStruct, this one is hand-coded as MapStruct
 * support is still in beta, and requires a manual step with an IDE.
 */
@Service
public class UserMapper {

    /**
     * Converts a list of {@link User} entities to a list of {@link UserDTO}.
     *
     * @param users the list of users to convert
     * @return a list of user DTOs
     */
    public List<UserDTO> usersToUserDTOs(List<User> users) {
        return users.stream().filter(Objects::nonNull).map(this::userToUserDTO).toList();
    }

    /**
     * Converts a {@link User} entity to a {@link UserDTO}.
     *
     * @param user the user entity to convert
     * @return the user DTO
     */
    public UserDTO userToUserDTO(User user) {
        return new UserDTO(user);
    }

    /**
     * Converts a list of {@link User} entities to a list of {@link AdminUserDTO}.
     *
     * @param users the list of users to convert
     * @return a list of admin user DTOs
     */
    public List<AdminUserDTO> usersToAdminUserDTOs(List<User> users) {
        return users.stream().filter(Objects::nonNull).map(this::userToAdminUserDTO).toList();
    }

    /**
     * Converts a {@link User} entity to an {@link AdminUserDTO}.
     *
     * @param user the user entity to convert
     * @return the admin user DTO
     */
    public AdminUserDTO userToAdminUserDTO(User user) {
        return new AdminUserDTO(user);
    }

    /**
     * Converts a list of {@link AdminUserDTO} to a list of {@link User} entities.
     *
     * @param userDTOs the list of admin user DTOs to convert
     * @return a list of user entities
     */
    public List<User> userDTOsToUsers(List<AdminUserDTO> userDTOs) {
        return userDTOs.stream().filter(Objects::nonNull).map(this::userDTOToUser).toList();
    }

    /**
     * Converts an {@link AdminUserDTO} to a {@link User} entity.
     * Includes mapping of authorities from strings to {@link Authority} objects.
     *
     * @param userDTO the admin user DTO to convert
     * @return the user entity
     */
    public User userDTOToUser(AdminUserDTO userDTO) {
        if (userDTO == null) {
            return null;
        } else {
            User user = new User();
            user.setId(userDTO.getId());
            user.setLogin(userDTO.getLogin());
            user.setFirstName(userDTO.getFirstName());
            user.setLastName(userDTO.getLastName());
            user.setEmail(userDTO.getEmail());
            user.setImageUrl(userDTO.getImageUrl());
            user.setActivated(userDTO.isActivated());
            user.setLangKey(userDTO.getLangKey());
            Set<Authority> authorities = this.authoritiesFromStrings(userDTO.getAuthorities());
            user.setAuthorities(authorities);
            return user;
        }
    }

    private Set<Authority> authoritiesFromStrings(Set<String> authoritiesAsString) {
        Set<Authority> authorities = new HashSet<>();

        if (authoritiesAsString != null) {
            authorities = authoritiesAsString
                .stream()
                .map(string -> {
                    Authority auth = new Authority();
                    auth.setName(string);
                    return auth;
                })
                .collect(Collectors.toSet());
        }

        return authorities;
    }

    /**
     * Converts a user ID to a {@link User} entity.
     *
     * @param id the ID to convert
     * @return a user entity with only the ID populated, or null if the ID is null
     */
    public User userFromId(Long id) {
        if (id == null) {
            return null;
        }
        User user = new User();
        user.setId(id);
        return user;
    }

    /**
     * Converts a {@link User} entity to a {@link UserDTO} containing only the ID.
     *
     * @param user the user entity to convert
     * @return a user DTO with only the ID populated
     */
    @Named("id")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    public UserDTO toDtoId(User user) {
        if (user == null) {
            return null;
        }
        UserDTO userDto = new UserDTO();
        userDto.setId(user.getId());
        return userDto;
    }

    /**
     * Converts a set of {@link User} entities to a set of {@link UserDTO} containing only the IDs.
     *
     * @param users the set of user entities to convert
     * @return a set of user DTOs with only the IDs populated
     */
    @Named("idSet")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    public Set<UserDTO> toDtoIdSet(Set<User> users) {
        if (users == null) {
            return Collections.emptySet();
        }

        Set<UserDTO> userSet = new HashSet<>();
        for (User userEntity : users) {
            userSet.add(this.toDtoId(userEntity));
        }

        return userSet;
    }

    /**
     * Converts a {@link User} entity to a {@link UserDTO} containing only the ID and login.
     *
     * @param user the user entity to convert
     * @return a user DTO with ID and login populated
     */
    @Named("login")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    public UserDTO toDtoLogin(User user) {
        if (user == null) {
            return null;
        }
        UserDTO userDto = new UserDTO();
        userDto.setId(user.getId());
        userDto.setLogin(user.getLogin());
        return userDto;
    }

    /**
     * Converts a set of {@link User} entities to a set of {@link UserDTO} containing only the ID and login.
     *
     * @param users the set of user entities to convert
     * @return a set of user DTOs with ID and login populated
     */
    @Named("loginSet")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    public Set<UserDTO> toDtoLoginSet(Set<User> users) {
        if (users == null) {
            return Collections.emptySet();
        }

        Set<UserDTO> userSet = new HashSet<>();
        for (User userEntity : users) {
            userSet.add(this.toDtoLogin(userEntity));
        }

        return userSet;
    }
}
