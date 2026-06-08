package com.wildme.wildbook_lite.auth;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Bridges our User entity → Spring Security's UserDetails contract.
 *
 *  - Spring Security calls loadUserByUsername(...) once per login (and once
 *    per request when we manually authenticate in JwtAuthenticationFilter).
 *  - Roles are exposed as authorities prefixed with "ROLE_" — Spring's
 *    convention for hasRole(...) checks.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
            .toList();

        return new AppPrincipal(
            user.getId(),
            user.getUsername(),
            user.getPasswordHash(),
            user.isEnabled(),
            authorities
        );
    }
}
