package com.jetmenu.order;

public enum OrderStatus {
    PENDING,
    READY,
    DELIVERED,
    PAID,
    CANCELLED,
    /**
     * iFood test order (isTest=true). Terminal status: lifecycle events never
     * promote it to PAID nor cancel it, so it stays out of earnings, dashboards
     * and notifications.
     */
    TEST
}

