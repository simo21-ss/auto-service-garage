package bg.softuni.garage.user;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Getter
public class GarageUserDetails implements UserDetails {

    private static final String ROLE_PREFIX = "ROLE_";

    private final UUID id;
    private final String username;
    private final String password;
    private final String fullName;
    private final RoleName role;
    private final boolean active;
    private final List<GrantedAuthority> authorities;

    public GarageUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.fullName = user.getFirstName() + " " + user.getLastName();
        this.role = user.getRole().getName();
        this.active = user.isActive();
        this.authorities = buildAuthorities(user);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    private static List<GrantedAuthority> buildAuthorities(User user) {
        Stream<String> roleAuthority = Stream.of(ROLE_PREFIX + user.getRole().getName().name());
        Stream<String> permissionAuthorities = user.getRole().getPermissions().stream()
                .map(permission -> permission.getName().name());

        return Stream.concat(roleAuthority, permissionAuthorities)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
