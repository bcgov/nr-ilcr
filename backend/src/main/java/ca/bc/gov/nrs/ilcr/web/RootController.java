package ca.bc.gov.nrs.ilcr.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RootController {

    private final String version;

    public RootController(@Value("${ilcr.version:0.0.1-SNAPSHOT}") String version) {
        this.version = version;
    }

    @GetMapping
    public AppInfoResponse getInfo() {
        return new AppInfoResponse("nr-ilcr-backend", version, "UP");
    }
}
