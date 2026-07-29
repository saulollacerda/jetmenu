package com.jetmenu.integration.anotaai;

import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.merchant.OpeningHour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component
public class OrderSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderSyncScheduler.class);

    private final MerchantRepository merchantRepository;
    private final AnotaAISyncService anotaAISyncService;

    public OrderSyncScheduler(MerchantRepository merchantRepository,
                               AnotaAISyncService anotaAISyncService) {
        this.merchantRepository = merchantRepository;
        this.anotaAISyncService = anotaAISyncService;
    }

    @Scheduled(fixedDelay = 600_000)
    @Async
    public void syncOpenMerchants() {
        ZonedDateTime current = now();
        merchantRepository.findAllByAnotaAiApiKeyIsNotNull()
                .stream()
                .filter(m -> isOpen(m.getOpeningHours(), current))
                .forEach(m -> {
                    log.info("[Anota.AI] sync automático iniciado — merchant={}", m.getId());
                    try {
                        anotaAISyncService.syncOrders(m.getId());
                    } catch (Exception e) {
                        log.error("[Anota.AI] sync automático falhou — merchant={}: {}", m.getId(), e.getMessage());
                    }
                });
    }

    ZonedDateTime now() {
        return ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
    }

    private boolean isOpen(List<OpeningHour> hours, ZonedDateTime current) {
        if (hours == null || hours.isEmpty()) return false;
        DayOfWeek today = current.getDayOfWeek();
        int nowMin = current.getHour() * 60 + current.getMinute();
        return hours.stream()
                .filter(h -> h.getDayOfWeek() == today && !h.isClosed())
                .filter(h -> h.getOpenTime() != null && h.getCloseTime() != null)
                .anyMatch(h -> {
                    int open = toMinutes(h.getOpenTime());
                    int close = toMinutes(h.getCloseTime());
                    return nowMin >= open && nowMin < close;
                });
    }

    private int toMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
