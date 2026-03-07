package com.example.backend_spring.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

import com.example.backend_spring.repository.UserAccountRepository;

@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repo;

    public DbUserDetailsService(UserAccountRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = repo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<String> roleNames = repo.findRoleNamesByUserId(user.getId());
        if (roleNames.isEmpty()) {
            roleNames = List.of("ROLE_USER");
        }

        var authorities = roleNames.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .disabled(!user.isEnabled())
                .authorities(authorities)
                .build();
    }
}
