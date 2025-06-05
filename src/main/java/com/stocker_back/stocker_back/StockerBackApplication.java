package com.stocker_back.stocker_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockerBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockerBackApplication.class, args);
	}

}
