package com.casamoreno.mercado_livre_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableFeignClients
@EnableAsync
public class MercadoLivreApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MercadoLivreApiApplication.class, args);
	}

}
