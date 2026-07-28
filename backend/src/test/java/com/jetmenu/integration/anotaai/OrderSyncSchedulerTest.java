package com.jetmenu.integration.anotaai;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.merchant.OpeningHour;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSyncScheduler")
class OrderSyncSchedulerTest {

    @Mock
    MerchantRepository merchantRepository;

    @Mock
    AnotaAISyncService anotaAISyncService;

    @InjectMocks
    OrderSyncScheduler scheduler;

    private UUID merchantId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        merchant = Merchant.builder()
                .id(merchantId)
                .build();
        merchant.setAnotaAiApiKey("key-123");
    }

    @Test
    @DisplayName("does not sync when merchant has no opening hours")
    void noOpeningHours() throws Exception {
        merchant.setOpeningHours(null);
        given(merchantRepository.findAllByAnotaAiApiKeyIsNotNull()).willReturn(List.of(merchant));

        scheduler.syncOpenMerchants();

        verify(anotaAISyncService, never()).syncOrders(any());
    }

    @Test
    @DisplayName("does not sync when merchant has empty opening hours list")
    void emptyOpeningHours() throws Exception {
        merchant.setOpeningHours(List.of());
        given(merchantRepository.findAllByAnotaAiApiKeyIsNotNull()).willReturn(List.of(merchant));

        scheduler.syncOpenMerchants();

        verify(anotaAISyncService, never()).syncOrders(any());
    }

    @Test
    @DisplayName("does not sync when today is marked as closed")
    void todayIsClosed() throws Exception {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
        DayOfWeek today = now.getDayOfWeek();
        OpeningHour closedHour = OpeningHour.builder()
                .dayOfWeek(today)
                .closed(true)
                .build();
        merchant.setOpeningHours(List.of(closedHour));
        given(merchantRepository.findAllByAnotaAiApiKeyIsNotNull()).willReturn(List.of(merchant));

        scheduler.syncOpenMerchants();

        verify(anotaAISyncService, never()).syncOrders(any());
    }

    @Test
    @DisplayName("does not sync when current time is outside opening hours")
    void outsideOpeningHours() throws Exception {
        ZonedDateTime outsideNow = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"))
                .withHour(4).withMinute(0);
        OpeningHour todayHour = OpeningHour.builder()
                .dayOfWeek(outsideNow.getDayOfWeek())
                .openTime("11:00")
                .closeTime("23:00")
                .closed(false)
                .build();

        OrderSyncScheduler spy = spy(new OrderSyncScheduler(merchantRepository, anotaAISyncService));
        doReturn(outsideNow).when(spy).now();

        merchant.setOpeningHours(List.of(todayHour));
        given(merchantRepository.findAllByAnotaAiApiKeyIsNotNull()).willReturn(List.of(merchant));

        spy.syncOpenMerchants();

        verify(anotaAISyncService, never()).syncOrders(any());
    }

    @Test
    @DisplayName("syncs when current time is within opening hours")
    void withinOpeningHours() throws Exception {
        ZonedDateTime openNow = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"))
                .withHour(14).withMinute(0);
        OpeningHour todayHour = OpeningHour.builder()
                .dayOfWeek(openNow.getDayOfWeek())
                .openTime("11:00")
                .closeTime("23:00")
                .closed(false)
                .build();

        OrderSyncScheduler spy = spy(new OrderSyncScheduler(merchantRepository, anotaAISyncService));
        doReturn(openNow).when(spy).now();

        merchant.setOpeningHours(List.of(todayHour));
        given(merchantRepository.findAllByAnotaAiApiKeyIsNotNull()).willReturn(List.of(merchant));

        spy.syncOpenMerchants();

        verify(anotaAISyncService, times(1)).syncOrders(merchantId);
    }

    @Test
    @DisplayName("exception in one merchant sync does not stop others")
    void exceptionDoesNotPropagate() throws Exception {
        UUID secondId = UUID.randomUUID();
        Merchant second = Merchant.builder().id(secondId).build();
        second.setAnotaAiApiKey("key-456");

        ZonedDateTime openNow = ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"))
                .withHour(14).withMinute(0);
        OpeningHour todayHour = OpeningHour.builder()
                .dayOfWeek(openNow.getDayOfWeek())
                .openTime("11:00")
                .closeTime("23:00")
                .closed(false)
                .build();

        merchant.setOpeningHours(List.of(todayHour));
        second.setOpeningHours(List.of(todayHour));

        OrderSyncScheduler spy = spy(new OrderSyncScheduler(merchantRepository, anotaAISyncService));
        doReturn(openNow).when(spy).now();

        given(merchantRepository.findAllByAnotaAiApiKeyIsNotNull()).willReturn(List.of(merchant, second));
        doThrow(new RuntimeException("AnotaAI down")).when(anotaAISyncService).syncOrders(merchantId);

        spy.syncOpenMerchants();

        verify(anotaAISyncService, times(1)).syncOrders(secondId);
    }
}
