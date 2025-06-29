package com.casamoreno.mercado_livre_api.domain;

import lombok.Builder;

@Builder
public record SyncEvent(
        String level,
        String message
) {}
