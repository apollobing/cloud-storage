package com.example.cloudstorage.service;

import com.example.cloudstorage.dto.AuthRequest;
import com.example.cloudstorage.entity.User;
import com.example.cloudstorage.exception.UserAlreadyExistsException;
import com.example.cloudstorage.repository.UserRepository;
import com.example.cloudstorage.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User register(AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username " + request.getUsername() + " is already taken");
        }
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User newUser = new User(request.getUsername(), encodedPassword);
        return userRepository.save(newUser);
    }

    @Override
    @NotNull
    public UserDetails loadUserByUsername(@NotNull String username) throws UsernameNotFoundException {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден: " + username));

        return new CustomUserDetails(
                user.getId(),
                user.getUsername(),
                user.getPassword()
        );
    }
}
