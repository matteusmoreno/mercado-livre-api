package com.casamoreno.mercado_livre_api.client.casa_moreno_client;

import com.casamoreno.mercado_livre_api.client.casa_moreno_client.dto.CasaMorenoLoginRequest;
import com.casamoreno.mercado_livre_api.client.casa_moreno_client.dto.CasaMorenoLoginResponse;
import com.casamoreno.mercado_livre_api.dto.ProductDetailsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "casaMorenoClient", url = "https://api.casa-moreno.com")
public interface CasaMorenoClient {

    @PostMapping("/login")
    ResponseEntity<CasaMorenoLoginResponse> login(@RequestBody CasaMorenoLoginRequest request);

    @GetMapping("/products/list-all")
    List<ProductDetailsResponse> listAllProducts(@RequestHeader("Authorization") String authorizationHeader);

    @GetMapping("/products/{id}")
    ProductDetailsResponse findProductById(@RequestHeader("Authorization") String authorizationHeader, @PathVariable("id") UUID id);

    @PutMapping("/products/update")
    ResponseEntity<Void> updateProduct(@RequestHeader("Authorization") String authorizationHeader, @RequestBody Map<String, Object> request);

}