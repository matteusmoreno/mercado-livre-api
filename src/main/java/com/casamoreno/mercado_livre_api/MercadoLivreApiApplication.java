package com.casamoreno.mercado_livre_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MercadoLivreApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(MercadoLivreApiApplication.class, args);
	}

}
