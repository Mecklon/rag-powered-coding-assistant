package com.mecklon.RagPoweredCodingAssistant.user;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Finds or creates a user from GitHub OAuth profile data and stores the
     * GitHub access token. Returns the persisted user.
     */
    @Transactional
    public User upsertFromGithub(String githubId, String login, String name,
                                 String email, String avatarUrl, String accessToken) {
        Optional<User> existing = userRepository.findByGithubId(githubId);
        if (existing.isPresent()) {
            User user = existing.get();
            user.setLogin(login);
            user.setName(name);
            user.setEmail(email);
            user.setAvatarUrl(avatarUrl);
            user.setAccessToken(accessToken);
            return userRepository.save(user);
        }
        User user = new User();
        user.setGithubId(githubId);
        user.setLogin(login);
        user.setName(name);
        user.setEmail(email);
        user.setAvatarUrl(avatarUrl);
        user.setAccessToken(accessToken);
        return userRepository.save(user);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByGithubId(String githubId) {
        return userRepository.findByGithubId(githubId);
    }

    /**
     * Returns the stored GitHub access token for the given user id, or null if
     * the user does not exist.
     */
    public String getAccessToken(Long userId) {
        return userRepository.findById(userId)
                .map(User::getAccessToken)
                .orElse(null);
    }
}