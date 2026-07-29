package com.jetmenu.order;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Exige que o pedido referencie um cliente: {@code customerId} preenchido ou
 * {@code customerName} não vazio. A violação é reportada no campo
 * {@code customerId} para manter o contrato de {@code fieldErrors} da API.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CustomerReferenceRequiredValidator.class)
public @interface CustomerReferenceRequired {

    String message() default "Cliente é obrigatório";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
