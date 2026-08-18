package bg.softuni.garage.user;

import bg.softuni.garage.user.dto.ProfileRequest;
import bg.softuni.garage.user.dto.RegisterRequest;

import java.util.List;
import java.util.UUID;

public interface UserService {

    User register(RegisterRequest request);

    User getById(UUID id);

    User getByUsername(String username);

    List<User> findAll();

    User updateProfile(UUID userId, ProfileRequest request);

    User changeRole(UUID userId, RoleName roleName, UUID actingUserId);

    User setActive(UUID userId, boolean active, UUID actingUserId);

    long countByRole(RoleName roleName);
}
