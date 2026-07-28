package com.jetmenu.integration.ifood;

public class IfoodReauthorizationRequiredException extends RuntimeException {
    public IfoodReauthorizationRequiredException() {
        super("iFood refresh token expired — merchant must re-authorize the application");
    }
}
