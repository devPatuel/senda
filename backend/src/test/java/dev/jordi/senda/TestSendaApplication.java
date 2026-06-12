package dev.jordi.senda;

import org.springframework.boot.SpringApplication;

public class TestSendaApplication {

	public static void main(String[] args) {
		SpringApplication.from(SendaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
