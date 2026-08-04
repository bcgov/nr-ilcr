package ca.bc.gov.nrs.ilcr.assignment.dto;

/**
 * The display identifiers of a mill from {@code THE.MILL}, joined into an assignment view. Kept small
 * so the assignment service can label {@link MillSubmitter} rows without pulling the whole mill list.
 *
 * @param millNumber the mill number (rendered {@code String}; may be null in the data)
 * @param millName the mill name (may be null)
 */
public record MillLabel(String millNumber, String millName) {
}
