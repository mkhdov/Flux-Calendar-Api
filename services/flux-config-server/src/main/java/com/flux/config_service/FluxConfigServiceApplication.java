package com.flux.config_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class FluxConfigServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FluxConfigServiceApplication.class, args);
	}

}
