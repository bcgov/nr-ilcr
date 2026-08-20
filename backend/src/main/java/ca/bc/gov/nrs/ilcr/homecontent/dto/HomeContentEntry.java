package ca.bc.gov.nrs.ilcr.homecontent.dto;

/**
 * One role-keyed Home message (Story 24.2 / UC-CNT-001). {@code role} is the {@code THE.ILCR_ROLE}
 * key ({@code LICENSEE} / {@code AUDITOR} / {@code ADMIN}); {@code messageText} is the stored rich-text
 * (HTML) message, or {@code null} when the role has no message yet.
 *
 * @param role the role key
 * @param messageText the rich-text message (HTML), may be {@code null}
 */
public record HomeContentEntry(String role, String messageText) {
}
