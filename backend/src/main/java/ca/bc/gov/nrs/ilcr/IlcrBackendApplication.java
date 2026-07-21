package ca.bc.gov.nrs.ilcr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class IlcrBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(IlcrBackendApplication.class, args);
    }
}
