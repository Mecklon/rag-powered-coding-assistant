package com.mecklon.RagPoweredCodingAssistant.user;

import java.util.Optional;

/**
 * JDBC-based repository for the users table using spring-jdbc's JdbcTemplate.
 */
public interface UserRepository {

    Optional<User> findByGithubId(String githubId);

    Optional<User> findById(Long id);

    User save(User user);

    void updateAccessToken(Long id, String accessToken);
}