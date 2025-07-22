package de.tum.cit.aet.service;

import static de.tum.cit.aet.util.ArtemisServer.TS1;
import static de.tum.cit.aet.util.ArtemisServer.TS3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import de.tum.cit.aet.IntegrationTest;
import de.tum.cit.aet.domain.ArtemisUser;
import de.tum.cit.aet.repository.ArtemisUserRepository;
import de.tum.cit.aet.service.artemis.ArtemisUserService;
import de.tum.cit.aet.service.dto.ArtemisUserForCreationDTO;
import de.tum.cit.aet.service.dto.ArtemisUserPatternDTO;
import de.tum.cit.aet.web.rest.errors.BadRequestAlertException;
import jakarta.transaction.Transactional;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
@Transactional
public class ArtemisUserServiceIT {

    @Autowired
    private ArtemisUserService artemisUserService;

    @MockitoBean
    private ArtemisUserRepository artemisUserRepository;

    private List<ArtemisUser> ts1Users;

    @BeforeEach
    public void setUp() {
        ArtemisUser userId1 = new ArtemisUser();
        userId1.setServer(TS1);
        userId1.setServerWideId(1);
        userId1.setUsername("testUn1");
        userId1.setId(1L);

        ArtemisUser userId3 = new ArtemisUser();
        userId3.setServer(TS1);
        userId3.setServerWideId(3);
        userId3.setUsername("testUn3");
        userId3.setId(3L);

        ts1Users = new LinkedList<>();
        ts1Users.add(userId1);
        ts1Users.add(userId3);

        when(artemisUserRepository.findAllByServer(TS1)).thenReturn(ts1Users);

        when(artemisUserRepository.findById(1L)).thenReturn(Optional.of(userId1));

        when(artemisUserRepository.save(any())).thenAnswer(invocation -> {
            var user = invocation.getArgument(0, ArtemisUser.class);
            if (user.getServer() == TS1) {
                ts1Users.add(user);
            }
            return user;
        });
    }

    @Test
    public void testCreateArtemisUser_noId_success() {
        var userDTO = new ArtemisUserForCreationDTO();
        userDTO.setUsername("testUn");
        userDTO.setPassword("testPw");

        var createdUser = artemisUserService.createArtemisUser(TS1, userDTO);
        verify(artemisUserRepository).save(createdUser);
        assertEquals(TS1, createdUser.getServer());
        assertEquals("testUn", createdUser.getUsername());
        assertEquals("testPw", createdUser.getPassword());
        assertEquals(2, createdUser.getServerWideId());
    }

    @Test
    public void testCreateArtemisUser_withId_success() {
        var userDTO = new ArtemisUserForCreationDTO();
        userDTO.setUsername("testUn");
        userDTO.setPassword("testPw");
        userDTO.setServerWideId(5);

        var createdUser = artemisUserService.createArtemisUser(TS1, userDTO);
        verify(artemisUserRepository).save(createdUser);
        assertEquals(TS1, createdUser.getServer());
        assertEquals("testUn", createdUser.getUsername());
        assertEquals("testPw", createdUser.getPassword());
        assertEquals(5, createdUser.getServerWideId());
    }

    @Test
    public void testCreateArtemisUser_withId_fail_onIdAlreadyUsed() {
        var userDTO = new ArtemisUserForCreationDTO();
        userDTO.setUsername("testUn");
        userDTO.setPassword("testPw");
        userDTO.setServerWideId(1);

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.createArtemisUser(TS1, userDTO));
        verify(artemisUserRepository, times(0)).save(any());
    }

    @Test
    public void testCreateArtemisUser_withId_fail_onIdInvalid() {
        var userDTO = new ArtemisUserForCreationDTO();
        userDTO.setUsername("testUn");
        userDTO.setPassword("testPw");
        userDTO.setServerWideId(-1);

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.createArtemisUser(TS1, userDTO));
        verify(artemisUserRepository, times(0)).save(any());
    }

    @Test
    public void testCreateArtemisUser_noId_fail_onUsernameInvalid() {
        var userDTO = new ArtemisUserForCreationDTO();
        userDTO.setUsername("");
        userDTO.setPassword("testPw");

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.createArtemisUser(TS1, userDTO));
        verify(artemisUserRepository, times(0)).save(any());
    }

    @Test
    public void testCreateArtemisUser_noId_fail_onPasswordInvalid() {
        var userDTO = new ArtemisUserForCreationDTO();
        userDTO.setUsername("testUn");
        userDTO.setPassword("");

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.createArtemisUser(TS1, userDTO));
        verify(artemisUserRepository, times(0)).save(any());
    }

    @Test
    public void testCreateArtemisUser_noId_fail_onUsernameAlreadyUsed() {
        var userDTO = new ArtemisUserForCreationDTO();
        userDTO.setUsername("testUn1");
        userDTO.setPassword("testPw");

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.createArtemisUser(TS1, userDTO));
        verify(artemisUserRepository, times(0)).save(any());
    }

    @Test
    public void testCreateFromPattern_success() {
        var pattern = new ArtemisUserPatternDTO();
        pattern.setFrom(5);
        pattern.setTo(8);
        pattern.setUsernamePattern("test_{i}");
        pattern.setPasswordPattern("test_pw_{i}");

        var createdUsers = artemisUserService.createArtemisUsersByPattern(TS1, pattern);
        verify(artemisUserRepository, times(3)).save(any());
        assertEquals(3, createdUsers.size());

        assertEquals("test_5", createdUsers.getFirst().getUsername());
        assertEquals("test_pw_5", createdUsers.getFirst().getPassword());
        assertEquals(5, createdUsers.getFirst().getServerWideId());

        assertEquals("test_6", createdUsers.get(1).getUsername());
        assertEquals("test_pw_6", createdUsers.get(1).getPassword());
        assertEquals(6, createdUsers.get(1).getServerWideId());

        assertEquals("test_7", createdUsers.get(2).getUsername());
        assertEquals("test_pw_7", createdUsers.get(2).getPassword());
        assertEquals(7, createdUsers.get(2).getServerWideId());
    }

    @Test
    public void testCreateFromPattern_success_skipOne() {
        var pattern = new ArtemisUserPatternDTO();
        pattern.setFrom(2);
        pattern.setTo(5);
        pattern.setUsernamePattern("test_{i}");
        pattern.setPasswordPattern("test_pw_{i}");

        // Number 3 is already used and should be skipped
        var createdUsers = artemisUserService.createArtemisUsersByPattern(TS1, pattern);
        verify(artemisUserRepository, times(2)).save(any());
        assertEquals(2, createdUsers.size());

        assertEquals("test_2", createdUsers.getFirst().getUsername());
        assertEquals("test_pw_2", createdUsers.getFirst().getPassword());
        assertEquals(2, createdUsers.getFirst().getServerWideId());

        assertEquals("test_4", createdUsers.get(1).getUsername());
        assertEquals("test_pw_4", createdUsers.get(1).getPassword());
        assertEquals(4, createdUsers.get(1).getServerWideId());
    }

    @Test
    public void testCreateFromPattern_fail_rangeInvalid() {
        var pattern = new ArtemisUserPatternDTO();
        pattern.setFrom(7);
        pattern.setTo(5);
        pattern.setUsernamePattern("test_{i}");
        pattern.setPasswordPattern("test_pw_{i}");

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.createArtemisUsersByPattern(TS1, pattern));
        verify(artemisUserRepository, times(0)).save(any());
    }

    @Test
    public void testCreateFromPattern_fail_rangeInvalid2() {
        var pattern = new ArtemisUserPatternDTO();
        pattern.setFrom(0);
        pattern.setTo(5);
        pattern.setUsernamePattern("test_{i}");
        pattern.setPasswordPattern("test_pw_{i}");

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.createArtemisUsersByPattern(TS1, pattern));
        verify(artemisUserRepository, times(0)).save(any());
    }

    @Test
    public void testCreateFromPattern_fail_usernamePatternInvalid() {
        var pattern = new ArtemisUserPatternDTO();
        pattern.setFrom(1);
        pattern.setTo(5);
        pattern.setUsernamePattern("test_i");
        pattern.setPasswordPattern("test_pw_{i}");

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.createArtemisUsersByPattern(TS1, pattern));
        verify(artemisUserRepository, times(0)).save(any());
    }

    @Test
    public void testCreateFromPattern_fail_serverNull() {
        var pattern = new ArtemisUserPatternDTO();
        pattern.setFrom(1);
        pattern.setTo(5);
        pattern.setUsernamePattern("test_{i}");
        pattern.setPasswordPattern("test_pw_{i}");

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.createArtemisUsersByPattern(null, pattern));
        verify(artemisUserRepository, times(0)).save(any());
    }

    @Test
    public void testCreateFromCsv_noIds_success() {
        MockMultipartFile mockMultipartFile = new MockMultipartFile(
            "file",
            "test.csv",
            "text/csv",
            "username,password\nuser1,pw1\nuser2,pw2".getBytes()
        );

        var createdUsers = artemisUserService.createArtemisUsersFromCSV(mockMultipartFile, TS1);
        verify(artemisUserRepository, times(2)).save(any());
        assertEquals(2, createdUsers.size());

        assertEquals("user1", createdUsers.getFirst().getUsername());
        assertEquals("pw1", createdUsers.getFirst().getPassword());
        assertEquals(2, createdUsers.getFirst().getServerWideId());

        assertEquals("user2", createdUsers.get(1).getUsername());
        assertEquals("pw2", createdUsers.get(1).getPassword());
        assertEquals(4, createdUsers.get(1).getServerWideId());
    }

    @Test
    public void testCreateFromCsv_withIds_success() {
        MockMultipartFile mockMultipartFile = new MockMultipartFile(
            "file",
            "test.csv",
            "text/csv",
            "id,username,password\n2,user1,pw1\n6,user2,pw2".getBytes()
        );

        var createdUsers = artemisUserService.createArtemisUsersFromCSV(mockMultipartFile, TS1);
        verify(artemisUserRepository, times(2)).save(any());
        assertEquals(2, createdUsers.size());

        assertEquals("user1", createdUsers.getFirst().getUsername());
        assertEquals("pw1", createdUsers.getFirst().getPassword());
        assertEquals(2, createdUsers.getFirst().getServerWideId());

        assertEquals("user2", createdUsers.get(1).getUsername());
        assertEquals("pw2", createdUsers.get(1).getPassword());
        assertEquals(6, createdUsers.get(1).getServerWideId());
    }

    @Test
    public void testCreateFromCsv_withSomeIds_success() {
        MockMultipartFile mockMultipartFile = new MockMultipartFile(
            "file",
            "test.csv",
            "text/csv",
            "id,username,password\n2,user1,pw1\n,user2,pw2".getBytes()
        );

        var createdUsers = artemisUserService.createArtemisUsersFromCSV(mockMultipartFile, TS1);
        verify(artemisUserRepository, times(2)).save(any());
        assertEquals(2, createdUsers.size());

        assertEquals("user1", createdUsers.getFirst().getUsername());
        assertEquals("pw1", createdUsers.getFirst().getPassword());
        assertEquals(2, createdUsers.getFirst().getServerWideId());

        assertEquals("user2", createdUsers.get(1).getUsername());
        assertEquals("pw2", createdUsers.get(1).getPassword());
        assertEquals(4, createdUsers.get(1).getServerWideId());
    }

    @Test
    public void testCreateFromCsv_withIds_success_skipOne() {
        MockMultipartFile mockMultipartFile = new MockMultipartFile(
            "file",
            "test.csv",
            "text/csv",
            "id,username,password\n2,user1,pw1\n3,user2,pw2".getBytes()
        );

        var createdUsers = artemisUserService.createArtemisUsersFromCSV(mockMultipartFile, TS1);
        verify(artemisUserRepository, times(1)).save(any());
        assertEquals(1, createdUsers.size());

        assertEquals("user1", createdUsers.getFirst().getUsername());
        assertEquals("pw1", createdUsers.getFirst().getPassword());
        assertEquals(2, createdUsers.getFirst().getServerWideId());
    }

    @Test
    public void testCreateFromCsv_withIds_success_skipId0() {
        MockMultipartFile mockMultipartFile = new MockMultipartFile(
            "file",
            "test.csv",
            "text/csv",
            "id,username,password\n0,user0,pw1\n2,user1,pw1\n6,user2,pw2".getBytes()
        );

        var createdUsers = artemisUserService.createArtemisUsersFromCSV(mockMultipartFile, TS1);
        verify(artemisUserRepository, times(2)).save(any());
        assertEquals(2, createdUsers.size());

        assertEquals("user1", createdUsers.getFirst().getUsername());
        assertEquals("pw1", createdUsers.getFirst().getPassword());
        assertEquals(2, createdUsers.getFirst().getServerWideId());

        assertEquals("user2", createdUsers.get(1).getUsername());
        assertEquals("pw2", createdUsers.get(1).getPassword());
        assertEquals(6, createdUsers.get(1).getServerWideId());
    }

    @Test
    public void testCreateFromCsv_noIds_fail_invalidCsv() {
        MockMultipartFile mockMultipartFile = new MockMultipartFile("file", "test.csv", "text/csv", "thisIsNotValid".getBytes());

        artemisUserService.createArtemisUsersFromCSV(mockMultipartFile, TS1);
        verify(artemisUserRepository, times(0)).save(any());
    }

    @Test
    public void testUpdateArtemisUser_success() {
        ArtemisUser newUser = new ArtemisUser();
        newUser.setId(1L);
        newUser.setServerWideId(1);
        newUser.setUsername("updated");
        newUser.setPassword("updatedPw");
        newUser.setServer(TS1);

        var updatedUser = artemisUserService.updateArtemisUser(1L, newUser);
        verify(artemisUserRepository).save(newUser);
        assertEquals("updated", updatedUser.getUsername());
        assertEquals("updatedPw", updatedUser.getPassword());
        assertEquals(1, updatedUser.getServerWideId());
    }

    @Test
    public void testUpdateArtemisUser_fail_invalidId() {
        ArtemisUser newUser = new ArtemisUser();
        newUser.setId(2L);
        newUser.setServerWideId(1);
        newUser.setUsername("updated");
        newUser.setPassword("updatedPw");
        newUser.setServer(TS1);

        assertThrows(IllegalArgumentException.class, () -> artemisUserService.updateArtemisUser(1L, newUser));
        verify(artemisUserRepository, times(0)).save(newUser);
    }

    @Test
    public void testUpdateArtemisUser_fail_invalidServer() {
        ArtemisUser newUser = new ArtemisUser();
        newUser.setId(1L);
        newUser.setServerWideId(1);
        newUser.setUsername("updated");
        newUser.setPassword("updatedPw");
        newUser.setServer(TS3);

        assertThrows(IllegalArgumentException.class, () -> artemisUserService.updateArtemisUser(1L, newUser));
        verify(artemisUserRepository, times(0)).save(newUser);
    }

    @Test
    public void testUpdateArtemisUser_fail_invalidUsername() {
        ArtemisUser newUser = new ArtemisUser();
        newUser.setId(1L);
        newUser.setServerWideId(1);
        newUser.setUsername("");
        newUser.setPassword("updatedPw");
        newUser.setServer(TS1);

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.updateArtemisUser(1L, newUser));
        verify(artemisUserRepository, times(0)).save(newUser);
    }

    @Test
    public void testUpdateArtemisUser_fail_invalidPassword() {
        ArtemisUser newUser = new ArtemisUser();
        newUser.setId(1L);
        newUser.setServerWideId(1);
        newUser.setUsername("updated");
        newUser.setPassword("");
        newUser.setServer(TS1);

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.updateArtemisUser(1L, newUser));
        verify(artemisUserRepository, times(0)).save(newUser);
    }

    @Test
    public void testUpdateArtemisUser_fail_usernameAlreadyUsed() {
        ArtemisUser newUser = new ArtemisUser();
        newUser.setId(1L);
        newUser.setServerWideId(1);
        newUser.setUsername("testUn3");
        newUser.setPassword("updatedPw");
        newUser.setServer(TS1);

        assertThrows(BadRequestAlertException.class, () -> artemisUserService.updateArtemisUser(1L, newUser));
        verify(artemisUserRepository, times(0)).save(newUser);
    }
}
