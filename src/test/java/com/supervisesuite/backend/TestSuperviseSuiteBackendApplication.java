package com.supervisesuite.backend;

import org.springframework.boot.SpringApplication;

public class TestSuperviseSuiteBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(SuperviseSuiteBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
