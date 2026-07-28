package com.jetmenu.order;

import com.jetmenu.ingredient.IngredientNameNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CustomerReferenceRequiredValidator
        implements ConstraintValidator<CustomerReferenceRequired, OrderRequest> {

    @Override
    public boolean isValid(OrderRequest request, ConstraintValidatorContext context) {
        boolean hasCustomerId = request.getCustomerId() != null;
        boolean hasCustomerName = !IngredientNameNormalizer.normalize(request.getCustomerName()).isEmpty();
        if (hasCustomerId || hasCustomerName) {
            return true;
        }

        // Reporta no nó customerId: o GlobalExceptionHandler só coleta fieldErrors,
        // e uma violação de classe (ObjectError) sumiria da resposta.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("customerId")
                .addConstraintViolation();
        return false;
    }
}
