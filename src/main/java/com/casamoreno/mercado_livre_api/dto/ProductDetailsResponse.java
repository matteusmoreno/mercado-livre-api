package com.casamoreno.mercado_livre_api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductDetailsResponse(
        UUID productId,
        String productCategory,
        Boolean isPromotional,
        String mercadoLivreId,
        String mercadoLivreUrl,
        String productTitle,
        String fullDescription,
        String productBrand,
        String productCondition,
        BigDecimal currentPrice,
        BigDecimal originalPrice,
        String discountPercentage,
        Integer installments,
        BigDecimal installmentValue,
        List<String> galleryImageUrls,
        String stockStatus
) {
}
