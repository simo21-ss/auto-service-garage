package bg.softuni.garage.config;

import bg.softuni.garage.user.Permission;
import bg.softuni.garage.user.PermissionName;
import bg.softuni.garage.user.PermissionRepository;
import bg.softuni.garage.user.Role;
import bg.softuni.garage.user.RoleName;
import bg.softuni.garage.user.RoleRepository;
import bg.softuni.garage.user.User;
import bg.softuni.garage.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(1)
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private static final Map<RoleName, Set<PermissionName>> ROLE_PERMISSIONS = Map.of(
            RoleName.CUSTOMER, EnumSet.of(
                    PermissionName.VEHICLE_MANAGE,
                    PermissionName.ORDER_BOOK),
            RoleName.MECHANIC, EnumSet.of(
                    PermissionName.ORDER_ASSIGN,
                    PermissionName.ORDER_WORK,
                    PermissionName.PART_RESERVE),
            RoleName.ADMIN, EnumSet.allOf(PermissionName.class));

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(PermissionRepository permissionRepository,
                           RoleRepository roleRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedPermissions();
        seedRoles();
        seedAccount("admin", "admin123", "Simeon", "Petrov", RoleName.ADMIN);
        seedAccount("mechanic", "mechanic123", "Georgi", "Ivanov", RoleName.MECHANIC);
        seedAccount("customer", "customer123", "Maria", "Dimitrova", RoleName.CUSTOMER);
    }

    private void seedPermissions() {
        EnumSet.allOf(PermissionName.class).stream()
                .filter(name -> permissionRepository.findByName(name).isEmpty())
                .forEach(name -> {
                    Permission permission = new Permission();
                    permission.setName(name);
                    permissionRepository.save(permission);
                    log.info("Seeded permission {}", name);
                });
    }

    private void seedRoles() {
        ROLE_PERMISSIONS.forEach((roleName, permissionNames) -> {
            Role role = roleRepository.findByName(roleName).orElseGet(Role::new);
            role.setName(roleName);
            role.setPermissions(permissionNames.stream()
                    .map(permissionRepository::findByName)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            roleRepository.save(role);
            log.info("Seeded role {} with {} permission(s)", roleName, permissionNames.size());
        });
    }

    private void seedAccount(String username,
                             String rawPassword,
                             String firstName,
                             String lastName,
                             RoleName roleName) {
        if (userRepository.existsByUsername(username)) {
            return;
        }

        Role role = roleRepository.findByName(roleName).orElseThrow();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@garage.bg");
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone("+359 88 000 0000");
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setRole(role);

        userRepository.save(user);
        log.info("Seeded {} account '{}'", roleName, username);
    }
}
