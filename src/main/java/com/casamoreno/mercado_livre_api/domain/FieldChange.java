package com.casamoreno.mercado_livre_api.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FieldChange {
    private Object oldValue;
    private Object newValue;
}