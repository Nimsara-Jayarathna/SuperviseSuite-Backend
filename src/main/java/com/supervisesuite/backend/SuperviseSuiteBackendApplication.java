package com.supervisesuite.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SuperviseSuiteBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SuperviseSuiteBackendApplication.class, args);
	}

}
