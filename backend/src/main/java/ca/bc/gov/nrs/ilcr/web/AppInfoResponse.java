package ca.bc.gov.nrs.ilcr.web;

public record AppInfoResponse(
        String name,
        String version,
        String status
) {
}
