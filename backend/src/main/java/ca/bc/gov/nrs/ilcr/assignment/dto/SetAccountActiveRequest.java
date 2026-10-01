package ca.bc.gov.nrs.ilcr.assignment.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Wire shape for flipping a licensee's ILCR account flag (AD-12 pin, Story 2.1). The user is a path
 * variable and the acting administrator comes from the token, so the body carries only the desired
 * state.
 *
 * <p>{@code active} is a boxed {@code @NotNull} rather than a primitive because a primitive would
 * bind an absent or null JSON field to {@code false} — turning a malformed {@code {}} body into a
 * silent deactivation instead of a 400.
 *
 * <p>Deactivation is refused while the user still holds any active mill assignment; clearing those
 * assignments first is the actual "switch this user off" workflow, and flipping this flag alone
 * neither grants nor removes access.
 *
 * @param active true to flag the account active, false to flag it inactive
 */
public record SetAccountActiveRequest(@NotNull Boolean active) {}
