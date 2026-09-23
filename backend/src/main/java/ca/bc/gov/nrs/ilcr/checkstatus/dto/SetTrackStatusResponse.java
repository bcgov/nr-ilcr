package ca.bc.gov.nrs.ilcr.checkstatus.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;

/**
 * The outcome of one of the two admin reversals on the Schedules 1&ndash;10 track (Story 18.1) —
 * Set to Draft and Set to Submit share this shape because they differ only in the two values it
 * carries.
 *
 * <p>A third record beside {@link VerifyReportResponse} rather than a reuse of it: the two are
 * structurally identical, but the name would lie about which transition produced the body, and
 * {@code VerifyReportResponse.trackStatus} is documented as always {@code V}. A field on the sweep
 * response was the other option and is worse — pinned sub-shapes are extended, never re-shaped
 * (AD-12), and adding one would force a regeneration of {@code CheckStatusWireContractIT}'s goldens
 * for no gain.
 *
 * @param trackStatus the track's status code after the transition — {@code D} for Set to Draft,
 *     {@code S} for Set to Submit; only ever present on a 200, since a refusal answers 409
 * @param message the verbatim legacy success text with its bundle key, resolved by the controller
 *     because the API returns final text rather than a code for the client to look up (AD-8).
 *     {@code sch1-10DraftMsg} for Set to Draft; {@code sch1-10SubmittedMsg} for Set to Submit,
 *     which legacy reused rather than minting a "verification reversed" message of its own
 */
public record SetTrackStatusResponse(String trackStatus, MessageInfo message) {}
