package bg.softuni.garage.user;

import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.DuplicateResourceException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import bg.softuni.garage.user.dto.ProfileRequest;
import bg.softuni.garage.user.dto.RegisterRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        Role role = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Default role is not configured"));

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPhone(request.getPhone());
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setRole(role);

        User saved = userRepository.save(user);
        log.info("Registered new customer account '{}' [{}]", saved.getUsername(), saved.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public User updateProfile(UUID userId, ProfileRequest request) {
        User user = getById(userId);
        String email = request.getEmail().trim().toLowerCase();

        if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        user.setEmail(email);
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setPhone(request.getPhone());

        User saved = userRepository.save(user);
        log.info("Updated profile of '{}'", saved.getUsername());
        return saved;
    }

    @Override
    @Transactional
    public User changeRole(UUID userId, RoleName roleName, UUID actingUserId) {
        if (userId.equals(actingUserId)) {
            throw new BusinessRuleException("You cannot change your own role");
        }

        User user = getById(userId);
        if (user.getRole().getName() == roleName) {
            throw new BusinessRuleException(
                    "'" + user.getUsername() + "' already has the " + roleName + " role");
        }
        if (user.getRole().getName() == RoleName.ADMIN && countByRole(RoleName.ADMIN) <= 1) {
            throw new BusinessRuleException("The last administrator account cannot be demoted");
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role " + roleName + " is not configured"));

        RoleName previous = user.getRole().getName();
        user.setRole(role);

        User saved = userRepository.save(user);
        log.info("Changed role of '{}' from {} to {}", saved.getUsername(), previous, roleName);
        return saved;
    }

    @Override
    @Transactional
    public User setActive(UUID userId, boolean active, UUID actingUserId) {
        if (userId.equals(actingUserId)) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }

        User user = getById(userId);
        if (user.isActive() == active) {
            throw new BusinessRuleException("The account is already "
                    + (active ? "active" : "deactivated"));
        }

        user.setActive(active);

        User saved = userRepository.save(user);
        log.info("Set account '{}' active flag to {}", saved.getUsername(), active);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByRole(RoleName roleName) {
        return userRepository.countByRoleName(roleName);
    }
}
