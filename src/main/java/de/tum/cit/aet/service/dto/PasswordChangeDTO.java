package de.tum.cit.aet.service.dto;

/**
 * A DTO representing a password change required data - current and new password.
 */
public record PasswordChangeDTO(String currentPassword, String newPassword) {}
