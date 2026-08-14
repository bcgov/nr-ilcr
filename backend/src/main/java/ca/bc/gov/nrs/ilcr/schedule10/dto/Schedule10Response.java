package ca.bc.gov.nrs.ilcr.schedule10.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The Schedule 10 aggregate document — the pinned AD-12 wire contract for every Schedule 10 story.
 * Stories 11.2 and 11.3 consume this shape; they must not re-pin it.
 *
 * <p>{@code trackStatus}, {@code editable}, {@code revisionCount} and {@code message} reuse the
 * cross-schedule sub-shapes pinned by Story 1.1 — never re-shaped here.
 *
 * <p>{@code NON_NULL}: a null value is omitted from the JSON entirely rather than serialized as
 * {@code null} or coerced to {@code 0}. That is load-bearing for Schedule 10, where an absent cost
 * is the normal production shape.
 *
 * @param millId the mill this document belongs to
 * @param year the reporting year
 * @param trackStatus the 1–10 track report status, straight from {@code ILCR_MILL_REPORT_STATUS}
 * @param editable server-authoritative write authority (AD-9)
 * @param pages the construction pages, ordered by id; empty is a valid state
 * @param codeLists the year-filtered dropdown lists, served in-document
 * @param message a success/informational message; absent on a plain read
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Schedule10Response(
    long millId,
    int year,
    String trackStatus,
    boolean editable,
    List<ConstructionPage> pages,
    Schedule10CodeLists codeLists,
    MessageInfo message) {

  /**
   * Returns a copy carrying a message. The copy seam exists so Story 11.2's save-echo reuses this
   * shape without re-shaping it.
   *
   * @param newMessage the message to attach
   * @return a copy of this document with the message set
   */
  public Schedule10Response withMessage(MessageInfo newMessage) {
    return new Schedule10Response(
        millId, year, trackStatus, editable, pages, codeLists, newMessage);
  }
}
