package com.jetmenu.merchant;

public class DuplicateMerchantException extends RuntimeException {

    public DuplicateMerchantException(String field) {
        super("Já existe um comerciante cadastrado com este " + field);
    }
}
