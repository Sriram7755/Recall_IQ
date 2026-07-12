package com.coderecall.backend.service;

import com.coderecall.backend.model.User;
import com.coderecall.backend.repository.UserRepository;
import com.coderecall.backend.security.UserPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserPrincipal loadUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
        return UserPrincipal.create(user);
    }

    public User processOAuthUser(String githubId, String username, String email, String avatarUrl) {
        return userRepository.findByGithubId(githubId)
                .map(existingUser -> {
                    existingUser.setUsername(username);
                    existingUser.setEmail(email);
                    existingUser.setAvatarUrl(avatarUrl);
                    existingUser.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setGithubId(githubId);
                    newUser.setUsername(username);
                    newUser.setEmail(email);
                    newUser.setAvatarUrl(avatarUrl);
                    newUser.setCreatedAt(LocalDateTime.now());
                    newUser.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(newUser);
                });
    }
}
