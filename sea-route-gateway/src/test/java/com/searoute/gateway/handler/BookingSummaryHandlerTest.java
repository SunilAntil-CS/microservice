package com.searoute.gateway.handler;

import com.searoute.gateway.dto.Booking;
import com.searoute.gateway.dto.Invoice;
import com.searoute.gateway.dto.Tracking;
import com.searoute.gateway.proxy.BookingServiceProxy;
import com.searoute.gateway.proxy.CargoTrackingServiceProxy;
import com.searoute.gateway.proxy.PaymentServiceProxy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link BookingSummaryHandler} using WebTestClient.
 * Mocks the three proxies to verify the combined summary response and partial failure handling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(BookingSummaryHandlerTest.TestRouter.class)
class BookingSummaryHandlerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private BookingServiceProxy bookingServiceProxy;

    @MockBean
    private CargoTrackingServiceProxy cargoTrackingServiceProxy;

    @MockBean
    private PaymentServiceProxy paymentServiceProxy;

    @Test
    void getSummary_returnsCombinedResponse_whenAllProxiesSucceed() {
        Booking booking = Booking.builder()
                .id("123")
                .reference("BR-001")
                .status("CONFIRMED")
                .customerId("cust-1")
                .containerIds(Collections.singletonList("TRK-1"))
                .build();
        Tracking tracking = Tracking.builder()
                .id("TRK-1")
                .bookingId("123")
                .status("IN_TRANSIT")
                .location("Port A")
                .lastUpdated("2025-01-15T10:00:00Z")
                .build();
        Invoice invoice = Invoice.builder()
                .id("INV-1")
                .bookingId("123")
                .status("PAID")
                .amount("1500.00")
                .currency("USD")
                .build();

        when(bookingServiceProxy.getBooking(eq("123"))).thenReturn(Mono.just(booking));
        when(cargoTrackingServiceProxy.getTracking(eq("TRK-1"))).thenReturn(Mono.just(tracking));
        when(paymentServiceProxy.getInvoiceByBookingId(eq("123"))).thenReturn(Mono.just(invoice));

        webTestClient.get()
                .uri("/test/bookings/123/summary")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.bookingId").isEqualTo("123")
                .jsonPath("$.customer").isEqualTo("cust-1")
                .jsonPath("$.cargo.length()").isEqualTo(1)
                .jsonPath("$.cargo[0].status").isEqualTo("IN_TRANSIT")
                .jsonPath("$.cargo[0].location").isEqualTo("Port A")
                .jsonPath("$.invoice.status").isEqualTo("PAID")
                .jsonPath("$.invoice.amount").isEqualTo("1500.00")
                .jsonPath("$.warnings").isEmpty();
    }

    @Test
    void getSummary_returnsPartialDataWithWarnings_whenPaymentFails() {
        Booking booking = Booking.builder()
                .id("456")
                .reference("BR-002")
                .status("CONFIRMED")
                .customerId("cust-2")
                .containerIds(Collections.emptyList())
                .build();
        when(bookingServiceProxy.getBooking(eq("456"))).thenReturn(Mono.just(booking));
        when(cargoTrackingServiceProxy.getTracking(eq("456"))).thenReturn(Mono.just(Tracking.empty()));
        when(paymentServiceProxy.getInvoiceByBookingId(eq("456")))
                .thenReturn(Mono.error(new RuntimeException("Payment service down")));

        webTestClient.get()
                .uri("/test/bookings/456/summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.bookingId").isEqualTo("456")
                .jsonPath("$.customer").isEqualTo("cust-2")
                .jsonPath("$.warnings").isArray()
                .jsonPath("$.warnings[0]").value(org.hamcrest.Matchers.containsString("Invoice"));
    }

    @Test
    void getSummary_returnsPartialDataWithWarnings_whenBookingUnavailable() {
        when(bookingServiceProxy.getBooking(eq("789"))).thenReturn(Mono.just(Booking.empty()));
        when(paymentServiceProxy.getInvoiceByBookingId(eq("789")))
                .thenReturn(Mono.just(Invoice.builder().status("PENDING").amount("100").currency("USD").build()));

        webTestClient.get()
                .uri("/test/bookings/789/summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.bookingId").isEqualTo("789")
                .jsonPath("$.warnings").isArray();
    }

    /**
     * Test router: exposes the handler at /test/bookings/{id}/summary so we can hit it with
     * WebTestClient without going through the gateway (avoids auth and route config).
     */
    @Configuration
    static class TestRouter {
        @Bean
        org.springframework.web.reactive.function.server.RouterFunction<ServerResponse> summaryRoute(
                BookingSummaryHandler handler) {
            return RouterFunctions.route(
                    org.springframework.web.reactive.function.server.RequestPredicates.GET("/test/bookings/{id}/summary"),
                    handler::getSummary
            );
        }
    }
}
