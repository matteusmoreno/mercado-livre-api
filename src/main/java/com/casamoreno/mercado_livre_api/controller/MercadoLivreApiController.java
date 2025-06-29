package com.casamoreno.mercado_livre_api.controller;

import com.casamoreno.mercado_livre_api.domain.ProductInfo;
import com.casamoreno.mercado_livre_api.service.ProductScraperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mercado-livre")
public class MercadoLivreApiController {

    private final ProductScraperService productScraperService;

    public MercadoLivreApiController(ProductScraperService productScraperService) {
        this.productScraperService = productScraperService;
    }

    @GetMapping("/product-info")
    public ResponseEntity<ProductInfo> getProductInfo(@RequestParam String url) {
        try {
            ProductInfo productInfo = productScraperService.scrapeFullProductInfo(url);
            return ResponseEntity.ok(productInfo);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}