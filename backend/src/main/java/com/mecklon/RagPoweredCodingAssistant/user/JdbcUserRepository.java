package com.mecklon.RagPoweredCodingAssistant.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation backed by spring-jdbc's JdbcTemplate.
 */
@Repository
public class JdbcUserRepository implements UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<User> ROW_MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("github_id"),
            rs.getString("login"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("avatar_url"),
            rs.getString("access_token"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    @Override
    public Optional<User> findByGithubId(String githubId) {
        String sql = "SELECT id, github_id, login, name, email, avatar_url, access_token, created_at, updated_at "
                + "FROM users WHERE github_id = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, githubId).stream().findFirst();
    }

    @Override
    public Optional<User> findById(Long id) {
        String sql = "SELECT id, github_id, login, name, email, avatar_url, access_token, created_at, updated_at "
                + "FROM users WHERE id = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            return insert(user);
        }
        return update(user);
    }

    private User insert(User user) {
        String sql = "INSERT INTO users (github_id, login, name, email, avatar_url, access_token, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, user.getGithubId());
            ps.setString(2, user.getLogin());
            ps.setString(3, user.getName());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getAvatarUrl());
            ps.setString(6, user.getAccessToken());
            ps.setTimestamp(7, Timestamp.valueOf(now));
            ps.setTimestamp(8, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        user.setId(key == null ? null : key.longValue());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }

    private User update(User user) {
        String sql = "UPDATE users SET login = ?, name = ?, email = ?, avatar_url = ?, access_token = ?, updated_at = ? "
                + "WHERE id = ?";
        jdbcTemplate.update(sql,
                user.getLogin(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getAccessToken(),
                Timestamp.valueOf(LocalDateTime.now()),
                user.getId());
        return user;
    }

    @Override
    public void updateAccessToken(Long id, String accessToken) {
        String sql = "UPDATE users SET access_token = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, accessToken, Timestamp.valueOf(LocalDateTime.now()), id);
    }
}