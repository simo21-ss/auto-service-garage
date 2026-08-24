package bg.softuni.garage.user;

import bg.softuni.garage.TestFixtures;
import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.DuplicateResourceException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import bg.softuni.garage.user.dto.ProfileRequest;
import bg.softuni.garage.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionRegistry sessionRegistry;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void registerHashesThePasswordAndAssignsTheCustomerRole() {
        when(userRepository.existsByUsername("ivan")).thenReturn(false);
        when(userRepository.existsByEmail("ivan@mail.bg")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER))
                .thenReturn(Optional.of(TestFixtures.role(RoleName.CUSTOMER)));
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User registered = userService.register(registerRequest("ivan", "ivan@mail.bg"));

        assertThat(registered.getPassword()).isEqualTo("$2a$10$hashed");
        assertThat(registered.getRole().getName()).isEqualTo(RoleName.CUSTOMER);
        assertThat(registered.isActive()).isTrue();
        assertThat(registered.getCreatedAt()).isNotNull();
    }

    @Test
    void registerNormalisesTheEmailToLowerCase() {
        when(userRepository.existsByUsername("ivan")).thenReturn(false);
        when(userRepository.existsByEmail("ivan@mail.bg")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER))
                .thenReturn(Optional.of(TestFixtures.role(RoleName.CUSTOMER)));
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User registered = userService.register(registerRequest("ivan", "  IVAN@Mail.BG "));

        assertThat(registered.getEmail()).isEqualTo("ivan@mail.bg");
    }

    @Test
    void registerRejectsATakenUsername() {
        when(userRepository.existsByUsername("ivan")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest("ivan", "ivan@mail.bg")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsATakenEmail() {
        when(userRepository.existsByUsername("ivan")).thenReturn(false);
        when(userRepository.existsByEmail("ivan@mail.bg")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest("ivan", "ivan@mail.bg")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void registerFailsWhenTheDefaultRoleIsMissing() {
        when(userRepository.existsByUsername("ivan")).thenReturn(false);
        when(userRepository.existsByEmail("ivan@mail.bg")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.register(registerRequest("ivan", "ivan@mail.bg")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByIdThrowsForAnUnknownUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByUsernameReturnsTheUser() {
        User user = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(userRepository.findByUsername("ivan")).thenReturn(Optional.of(user));

        assertThat(userService.getByUsername("ivan").getUsername()).isEqualTo("ivan");
    }

    @Test
    void getByUsernameThrowsForAnUnknownUser() {
        when(userRepository.findByUsername("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByUsername("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findAllReturnsEveryAccount() {
        when(userRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(TestFixtures.user("ivan", RoleName.CUSTOMER)));

        assertThat(userService.findAll()).hasSize(1);
    }

    @Test
    void updateProfileAppliesTheNewDetails() {
        User user = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User updated = userService.updateProfile(user.getId(), profileRequest("new@mail.bg"));

        assertThat(updated.getEmail()).isEqualTo("new@mail.bg");
        assertThat(updated.getFirstName()).isEqualTo("Ivan");
    }

    @Test
    void updateProfileRejectsAnEmailOwnedBySomeoneElse() {
        User user = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("taken@mail.bg")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(user.getId(), profileRequest("taken@mail.bg")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateProfileAllowsKeepingTheSameEmail() {
        User user = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User updated = userService.updateProfile(user.getId(), profileRequest(user.getEmail()));

        assertThat(updated.getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void changeRolePromotesTheUser() {
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of());
        User target = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(roleRepository.findByName(RoleName.MECHANIC))
                .thenReturn(Optional.of(TestFixtures.role(RoleName.MECHANIC)));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User updated = userService.changeRole(target.getId(), RoleName.MECHANIC, UUID.randomUUID());

        assertThat(updated.getRole().getName()).isEqualTo(RoleName.MECHANIC);
    }

    @Test
    void anAdministratorCannotChangeTheirOwnRole() {
        UUID selfId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.changeRole(selfId, RoleName.CUSTOMER, selfId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("your own role");
    }

    @Test
    void changingToTheSameRoleIsRejected() {
        User target = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() ->
                userService.changeRole(target.getId(), RoleName.CUSTOMER, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has");
    }

    @Test
    void theLastAdministratorCannotBeDemoted() {
        User admin = TestFixtures.user("admin", RoleName.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleName(RoleName.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() ->
                userService.changeRole(admin.getId(), RoleName.CUSTOMER, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("last administrator");
    }

    @Test
    void changeRoleFailsWhenTheTargetRoleIsNotConfigured() {
        User target = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(roleRepository.findByName(RoleName.MECHANIC)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.changeRole(target.getId(), RoleName.MECHANIC, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void suspendingAnAccountFlipsTheActiveFlag() {
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of());
        User target = TestFixtures.user("ivan", RoleName.CUSTOMER);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User updated = userService.setActive(target.getId(), false, UUID.randomUUID());

        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void anAdministratorCannotSuspendThemselves() {
        UUID selfId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.setActive(selfId, false, selfId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("your own account");
    }

    @Test
    void suspendingAnAlreadySuspendedAccountIsRejected() {
        User target = TestFixtures.user("ivan", RoleName.CUSTOMER);
        target.setActive(false);
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> userService.setActive(target.getId(), false, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void countByRoleDelegatesToTheRepository() {
        when(userRepository.countByRoleName(RoleName.ADMIN)).thenReturn(3L);

        assertThat(userService.countByRole(RoleName.ADMIN)).isEqualTo(3L);
    }

    private RegisterRequest registerRequest(String username, String email) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword("secret123");
        request.setConfirmPassword("secret123");
        request.setFirstName("Ivan");
        request.setLastName("Kolev");
        request.setPhone("+359881234567");
        return request;
    }

    private ProfileRequest profileRequest(String email) {
        ProfileRequest request = new ProfileRequest();
        request.setEmail(email);
        request.setFirstName("Ivan");
        request.setLastName("Kolev");
        request.setPhone("+359887654321");
        return request;
    }
}
