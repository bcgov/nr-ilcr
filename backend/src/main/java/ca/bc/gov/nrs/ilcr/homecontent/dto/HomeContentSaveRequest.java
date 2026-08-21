package ca.bc.gov.nrs.ilcr.homecontent.dto;

/**
 * The Content Editing save payload (Story 24.2 / UC-CNT-001): all three role messages, saved
 * together atomically (A-3). Each is required rich-text (HTML); a blank editor is rejected
 * per-field (FLD-001).
 *
 * @param licensee the Licensee welcome message
 * @param auditor the Auditor-keyed welcome message
 * @param administrator the Administrator welcome message
 */
public record HomeContentSaveRequest(String licensee, String auditor, String administrator) {}
