package com.jetmenu.fee;

public class DuplicateFeeException extends RuntimeException {

    public DuplicateFeeException(String field) {
        super("Já existe uma taxa com este " + field);
    }
}
