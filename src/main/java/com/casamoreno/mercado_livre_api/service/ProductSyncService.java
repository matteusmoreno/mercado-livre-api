package com.casamoreno.mercado_livre_api.service;

import com.casamoreno.mercado_livre_api.client.casa_moreno_client.CasaMorenoClient;
import com.casamoreno.mercado_livre_api.client.casa_moreno_client.dto.CasaMorenoLoginRequest;
import com.casamoreno.mercado_livre_api.client.casa_moreno_client.dto.CasaMorenoLoginResponse;
import com.casamoreno.mercado_livre_api.domain.FieldChange;
import com.casamoreno.mercado_livre_api.domain.ProductComparisonResult;
import com.casamoreno.mercado_livre_api.domain.ProductInfo;
import com.casamoreno.mercado_livre_api.domain.SyncEvent;
import com.casamoreno.mercado_livre_api.dto.ProductDetailsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ProductSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ProductSyncService.class);

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED_BOLD = "\u001B[1;31m";

    private final ProductScraperService scraperService;
    private final CasaMorenoClient casaMorenoClient;
    private final ObjectMapper objectMapper;

    private final String backendUsername;
    private final String backendPassword;

    public ProductSyncService(
            ProductScraperService scraperService,
            CasaMorenoClient casaMorenoClient,
            ObjectMapper objectMapper,
            @Value("${backend.credentials.username}") String backendUsername,
            @Value("${backend.credentials.password}") String backendPassword) {
        this.scraperService = scraperService;
        this.casaMorenoClient = casaMorenoClient;
        this.objectMapper = objectMapper;
        this.backendUsername = backendUsername;
        this.backendPassword = backendPassword;
    }

    @Async
    public CompletableFuture<Void> runFullSynchronization(SseEmitter emitter) {
        long successfulUpdatesCount = 0; // Contador de atualizações bem-sucedidas
        List<ProductComparisonResult> results = new ArrayList<>();

        try {
            sendSseEvent(emitter, "INFO", "Iniciando processo de sincronização e atualização...");

            String authToken = getAuthToken();
            sendSseEvent(emitter, "INFO", "Autenticação no backend realizada com sucesso.");

            List<ProductDetailsResponse> productDtosFromDb = casaMorenoClient.listAllProducts(authToken);
            sendSseEvent(emitter, "INFO", "Encontrados " + productDtosFromDb.size() + " produtos para verificação.");

            for (int i = 0; i < productDtosFromDb.size(); i++) {
                ProductDetailsResponse productDto = productDtosFromDb.get(i);
                String productIdentifier = String.format("'%s' (ID: %s)", productDto.productTitle(), productDto.productId());

                if (productDto.mercadoLivreUrl() == null || productDto.mercadoLivreUrl().isEmpty()) {
                    sendSseEvent(emitter, "ERROR", "--> Produto " + productIdentifier + " pulado: URL ausente.");
                    continue;
                }

                try {
                    sendSseEvent(emitter, "INFO", String.format("Verificando %d/%d: %s", (i + 1), productDtosFromDb.size(), productIdentifier));

                    ProductInfo oldProductInfo = toProductInfo(productDto);
                    ProductInfo newProductInfo = scraperService.scrapeFullProductInfo(oldProductInfo.getMercadoLivreUrl());
                    ProductComparisonResult comparison = compareFields(oldProductInfo, newProductInfo);

                    if (comparison.isHasChanges()) {
                        sendSseEvent(emitter, "WARN", "--> Mudanças detectadas para " + productIdentifier + ". Preparando para atualizar...");
                        boolean updatedSuccessfully = updateProductInBackend(emitter, authToken, productDto.productId(), comparison.getChangedFields());
                        if (updatedSuccessfully) {
                            successfulUpdatesCount++; // Incrementa o contador
                        }
                    } else {
                        sendSseEvent(emitter, "SUCCESS", "--> Produto " + productIdentifier + ": OK, sem alterações.");
                    }

                    TimeUnit.SECONDS.sleep(7);

                } catch (Exception e) {
                    sendSseEvent(emitter, "ERROR", "--> Falha ao processar " + productIdentifier + ": " + e.getMessage());
                }
            }

            sendFinalReport(emitter, productDtosFromDb.size(), successfulUpdatesCount);

        } catch (Exception e) {
            sendSseEvent(emitter, "ERROR", "ERRO CRÍTICO: " + e.getMessage());
            logger.error(ANSI_RED_BOLD + "ERRO CRÍTICO NO PROCESSO DE SINCRONIZAÇÃO: Processo abortado." + ANSI_RESET, e);
        } finally {
            emitter.complete();
            logger.info("Conexão SSE finalizada.");
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Monta o payload e envia a requisição de atualização para o backend.
     * @return true se a atualização foi bem-sucedida, false caso contrário.
     */
    private boolean updateProductInBackend(SseEmitter emitter, String authToken, UUID productId, Map<String, FieldChange> changedFields) {
        Map<String, Object> updatePayload = new HashMap<>();
        updatePayload.put("productId", productId);

        changedFields.forEach((fieldName, fieldChange) -> {
            updatePayload.put(fieldName, fieldChange.getNewValue());
            sendSseEvent(emitter, "WARN", String.format("    - Campo '%s' será atualizado para: [%s]", fieldName, fieldChange.getNewValue()));
        });

        try {
            sendSseEvent(emitter, "INFO", "    => Enviando atualização para o backend...");
            ResponseEntity<Void> updateResponse = casaMorenoClient.updateProduct(authToken, updatePayload);
            if (updateResponse.getStatusCode().is2xxSuccessful()) {
                sendSseEvent(emitter, "SUCCESS", "    => Produto atualizado com sucesso no backend!");
                return true;
            } else {
                sendSseEvent(emitter, "ERROR", "    => Falha ao atualizar no backend. Status: " + updateResponse.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            sendSseEvent(emitter, "ERROR", "    => ERRO ao chamar a API de atualização: " + e.getMessage());
            logger.error("Erro detalhado na chamada de update para o produto {}", productId, e);
            return false;
        }
    }

    private String getAuthToken() {
        CasaMorenoLoginRequest loginRequest = new CasaMorenoLoginRequest(backendUsername, backendPassword);
        ResponseEntity<CasaMorenoLoginResponse> response = casaMorenoClient.login(loginRequest);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().token() != null) {
            return "Bearer " + response.getBody().token();
        } else {
            throw new RuntimeException("Falha ao obter token de autenticação do backend.");
        }
    }

    private ProductInfo toProductInfo(ProductDetailsResponse dto) {
        return ProductInfo.builder()
                .mercadoLivreId(dto.mercadoLivreId())
                .mercadoLivreUrl(dto.mercadoLivreUrl())
                .productTitle(dto.productTitle())
                .fullDescription(dto.fullDescription())
                .productBrand(dto.productBrand())
                .productCondition(dto.productCondition())
                .currentPrice(dto.currentPrice())
                .originalPrice(dto.originalPrice())
                .discountPercentage(dto.discountPercentage())
                .installments(dto.installments())
                .installmentValue(dto.installmentValue())
                .galleryImageUrls(dto.galleryImageUrls())
                .stockStatus(dto.stockStatus())
                .build();
    }

    private ProductComparisonResult compareFields(ProductInfo oldInfo, ProductInfo newInfo) {
        Map<String, FieldChange> changes = new HashMap<>();
        comparePrices(changes, "currentPrice", oldInfo.getCurrentPrice(), newInfo.getCurrentPrice());
        comparePrices(changes, "originalPrice", oldInfo.getOriginalPrice(), newInfo.getOriginalPrice());
        compareAndRegisterChange(changes, "discountPercentage", oldInfo.getDiscountPercentage(), newInfo.getDiscountPercentage());
        compareAndRegisterChange(changes, "installments", oldInfo.getInstallments(), newInfo.getInstallments());
        comparePrices(changes, "installmentValue", oldInfo.getInstallmentValue(), newInfo.getInstallmentValue());
        compareAndRegisterChange(changes, "stockStatus", oldInfo.getStockStatus(), newInfo.getStockStatus());

        boolean hasChanges = !changes.isEmpty();
        return ProductComparisonResult.builder().hasChanges(hasChanges).changedFields(changes).build();
    }

    private void compareAndRegisterChange(Map<String, FieldChange> changes, String fieldName, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.put(fieldName, new FieldChange(oldValue, newValue));
        }
    }

    private void comparePrices(Map<String, FieldChange> changes, String fieldName, BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null && newValue != null) {
            changes.put(fieldName, new FieldChange(null, newValue)); return;
        }
        if (oldValue != null && newValue == null) {
            changes.put(fieldName, new FieldChange(oldValue, null)); return;
        }
        if (oldValue == null) return;
        if (oldValue.compareTo(newValue) != 0) {
            changes.put(fieldName, new FieldChange(oldValue, newValue));
        }
    }

    private void sendFinalReport(SseEmitter emitter, int totalProducts, long successfulUpdates) {
        sendSseEvent(emitter, "INFO", "======================================================");
        sendSseEvent(emitter, "INFO", "RELATÓRIO DE SINCRONIZAÇÃO FINALIZADO");
        sendSseEvent(emitter, "INFO", "======================================================");

        sendSseEvent(emitter, "INFO", "Total de produtos verificados: " + totalProducts);
        sendSseEvent(emitter, "SUCCESS", "Total de produtos atualizados no backend: " + successfulUpdates);
        sendSseEvent(emitter, "INFO", "======================================================");
    }

    private void sendSseEvent(SseEmitter emitter, String level, String message) {
        SyncEvent event = SyncEvent.builder().level(level).message(message).build();
        try {
            emitter.send(SseEmitter.event().name("sync-update").data(objectMapper.writeValueAsString(event)));
        } catch (IOException e) {
            logger.warn("Falha ao enviar evento SSE para o cliente: {}", e.getMessage());
        }
    }
}