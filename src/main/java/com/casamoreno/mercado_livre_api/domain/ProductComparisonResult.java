package com.casamoreno.mercado_livre_api.domain;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID; // Importe UUID

@Data
@Builder
public class ProductComparisonResult {
    private String mercadoLivreId;
    private UUID backendProductId;
    private String backendProductTitle;
    private boolean hasChanges;
    private String message;
    private Map<String, FieldChange> changedFields;
}