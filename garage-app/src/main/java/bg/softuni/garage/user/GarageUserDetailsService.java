package bg.softuni.garage.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GarageUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public GarageUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(GarageUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user " + username));
    }
}
