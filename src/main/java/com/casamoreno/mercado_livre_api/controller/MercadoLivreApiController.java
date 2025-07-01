package com.casamoreno.mercado_livre_api.controller;

import com.casamoreno.mercado_livre_api.domain.ProductInfo;
import com.casamoreno.mercado_livre_api.service.ProductScraperService;
import com.casamoreno.mercado_livre_api.service.ProductSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/mercado-livre")
public class MercadoLivreApiController {

    private final ProductScraperService productScraperService;
    private final ProductSyncService productSyncService;

    public MercadoLivreApiController(ProductScraperService productScraperService, ProductSyncService productSyncService) {
        this.productScraperService = productScraperService;
        this.productSyncService = productSyncService;
    }

    @GetMapping("/product-info")
    public ResponseEntity<?> getProductInfo(@RequestParam String url) {
        try {
            ProductInfo productInfo = productScraperService.scrapeFullProductInfo(url);
            return ResponseEntity.ok(productInfo);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Falha ao processar a URL: " + e.getMessage());
        }
    }

    @GetMapping("/sync/stream")
    public SseEmitter streamSyncEvents() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        productSyncService.runFullSynchronization(emitter);

        return emitter;
    }
}