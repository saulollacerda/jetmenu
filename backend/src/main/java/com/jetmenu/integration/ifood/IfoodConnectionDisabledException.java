package com.jetmenu.integration.ifood;

/**
 * Thrown when a merchant tries to link an iFood account while the connection
 * flow is disabled (ifood.connection-enabled=false, e.g. homologation pending).
 */
public class IfoodConnectionDisabledException extends RuntimeException {

    public IfoodConnectionDisabledException() {
        super("iFood connection flow is disabled");
    }
}
