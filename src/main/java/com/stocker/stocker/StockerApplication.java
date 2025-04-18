package com.stocker.stocker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EntityScan(basePackages = {"com.stocker.stocker.domain"})
@EnableJpaRepositories(basePackages = {"com.stocker.stocker.repository"})
@EnableAsync
public class StockerApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockerApplication.class, args);
	}

} 