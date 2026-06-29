package com.trimly.backend.security;

import com.trimly.backend.entity.User;
import com.trimly.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String normalized = identifier.trim();

        User user = userRepository.findByEmail(normalized.toLowerCase())
                .or(() -> userRepository.findByPhone(normalized))
                .orElseThrow(() -> new UsernameNotFoundException("No user found: " + normalized));

        if (user.isDeleted()) {
            throw new UsernameNotFoundException("No user found: " + normalized);
        }

        return new CustomUserDetails(user);
    }
}