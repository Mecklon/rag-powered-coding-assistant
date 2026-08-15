package com.mecklon.RagPoweredCodingAssistant.auth;

/**
 * Principal placed in the SecurityContext after JWT validation.
 */
public record AuthenticatedUser(Long id, String githubId, String login) {
}