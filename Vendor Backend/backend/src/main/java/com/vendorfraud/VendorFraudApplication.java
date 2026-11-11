package com.vendorfraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Fraudulent Vendor Bank-Change Detection Workflow.
 *
 * <p>The application is a modular monolith: every business capability
 * (vendor, bank-change, approval, payment, override, notification, audit)
 * lives in its own package and talks to its peers through services only.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class VendorFraudApplication {

    public static void main(String[] args) {
        SpringApplication.run(VendorFraudApplication.class, args);
    }
}
