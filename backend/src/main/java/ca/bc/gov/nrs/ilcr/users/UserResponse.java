package ca.bc.gov.nrs.ilcr.users;

public record UserResponse(
        Long id,
        String name,
        String email
) {
}
