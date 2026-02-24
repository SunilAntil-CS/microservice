package com.searoute.gateway.handler;

import com.searoute.gateway.dto.Booking;
import com.searoute.gateway.dto.BookingSummaryResponse;
import com.searoute.gateway.dto.Invoice;
import com.searoute.gateway.dto.Tracking;
import com.searoute.gateway.proxy.BookingServiceProxy;
import com.searoute.gateway.proxy.CargoTrackingServiceProxy;
import com.searoute.gateway.proxy.PaymentServiceProxy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler for the booking summary (API composition) endpoint.
 * <p>
 * <strong>API composition</strong> reduces round trips for the client: instead of the client
 * calling booking, tracking, and payment services separately, this handler fetches all data
 * and returns a single combined response. We run the backend calls in parallel using
 * {@link Mono#zip}, which improves performance compared to sequential calls.
 * <p>
 * <strong>Graceful degradation:</strong> if one service fails (timeout, 5xx, circuit open),
 * we log a warning and continue with {@code null} or a default for that part, and add a
 * warning to the response so the client still receives partial data.
 * <p>
 * <strong>Metrics:</strong> we record a counter and a timer for this handler to help monitor
 * system health and performance (e.g. call rate and latency). These are Micrometer metrics
 * exposed at {@code /actuator/prometheus} for Prometheus to scrape; we do not push to
 * Prometheus. <strong>Zipkin</strong> is separate: distributed tracing (Brave/Micrometer
 * Tracing) runs automatically for each request and pushes spans to Zipkin; we do not
 * instrument Zipkin in this class—the gateway and WebClient integrations handle it.
 * <p>
 * <strong>Custom span:</strong> we add a span {@code summary-merge} around the step that
 * merges booking, cargo, and invoice into {@link BookingSummaryResponse}. That step is
 * in-memory only (no HTTP call), so it would not appear in Zipkin otherwise; the span
 * makes composition time visible in the trace.
 */
@Component
public class BookingSummaryHandler {

    private static final Logger log = LoggerFactory.getLogger(BookingSummaryHandler.class);

    private static final String METRIC_CALLS = "booking.summary.calls";
    private static final String METRIC_DURATION = "booking.summary.duration";
    private static final String SPAN_SUMMARY_MERGE = "summary-merge";

    private final BookingServiceProxy bookingServiceProxy;
    private final CargoTrackingServiceProxy cargoTrackingServiceProxy;
    private final PaymentServiceProxy paymentServiceProxy;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public BookingSummaryHandler(BookingServiceProxy bookingServiceProxy,
                                 CargoTrackingServiceProxy cargoTrackingServiceProxy,
                                 PaymentServiceProxy paymentServiceProxy,
                                 MeterRegistry meterRegistry,
                                 Tracer tracer) {
        this.bookingServiceProxy = bookingServiceProxy;
        this.cargoTrackingServiceProxy = cargoTrackingServiceProxy;
        this.paymentServiceProxy = paymentServiceProxy;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    /**
     * Returns a combined booking summary: booking details, cargo/tracking list, and invoice
     * status. Uses Mono.zip to run the three backend calls in parallel. On partial failure,
     * returns whatever data is available and adds a warning.
     * Records metrics (call count and duration) for monitoring health and performance.
     */
    public Mono<ServerResponse> getSummary(ServerRequest request) {
        String bookingId = request.pathVariable("id");
        // Prometheus metrics: counter and timer are read by Prometheus when it scrapes /actuator/prometheus.
        meterRegistry.counter(METRIC_CALLS).increment();
        Timer.Sample durationSample = Timer.start(meterRegistry);
        // Zipkin: tracing for this request is handled by the gateway/tracing integration; spans are sent to Zipkin separately.
        List<String> warnings = new ArrayList<>();

        // Cache so that both zip and fetchCargoList use the same result (single backend call).
        Mono<Booking> bookingMono = bookingServiceProxy.getBooking(bookingId)
                .doOnError(t -> {
                    log.warn("Booking service failed for id={}: {}", bookingId, t.getMessage());
                })
                .onErrorReturn(Booking.empty())
                .cache();

        Mono<List<BookingSummaryResponse.CargoItem>> cargoMono = bookingMono
                .flatMap(booking -> fetchCargoList(booking, warnings))
                .defaultIfEmpty(Collections.emptyList());

        Mono<BookingSummaryResponse.InvoiceSummary> invoiceMono = paymentServiceProxy
                .getInvoiceByBookingId(bookingId)
                .doOnError(t -> {
                    log.warn("Payment service failed for bookingId={}: {}", bookingId, t.getMessage());
                })
                .map(BookingSummaryHandler::toInvoiceSummary)
                .onErrorReturn(BookingSummaryResponse.InvoiceSummary.builder()
                        .status("UNAVAILABLE")
                        .amount(null)
                        .currency(null)
                        .build());

        // Run booking + cargo + invoice in parallel (cargo depends on booking, so we zip booking with cargo and invoice)
        Mono<BookingSummaryResponse> summaryMono = Mono.zip(bookingMono, cargoMono, invoiceMono)
                .map(tuple -> {
                    Span mergeSpan = tracer.nextSpan().name(SPAN_SUMMARY_MERGE).start();
                    try {
                        Booking booking = tuple.getT1();
                        List<BookingSummaryResponse.CargoItem> cargo = tuple.getT2();
                        BookingSummaryResponse.InvoiceSummary invoice = tuple.getT3();
                        if ("UNAVAILABLE".equals(booking.getStatus())) {
                            warnings.add("Booking service unavailable");
                        }
                        if (invoice != null && "UNAVAILABLE".equals(invoice.getStatus())) {
                            warnings.add("Invoice service unavailable");
                        }
                        return BookingSummaryResponse.builder()
                                .bookingId(bookingId)
                                .customer(booking.getCustomerId())
                                .cargo(cargo)
                                .invoice(invoice)
                                .warnings(warnings.isEmpty() ? null : warnings)
                                .build();
                    } finally {
                        mergeSpan.end();
                    }
                });

        return summaryMono
                .doFinally(signalType -> durationSample.stop(meterRegistry.timer(METRIC_DURATION)))
                .flatMap(summary -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(summary));
    }

    private Mono<List<BookingSummaryResponse.CargoItem>> fetchCargoList(Booking booking, List<String> warnings) {
        List<String> containerIds = booking.getContainerIds();
        if (containerIds == null || containerIds.isEmpty()) {
            // No container IDs: try fetching one tracking by booking ID (if we have one)
            String bookingId = booking.getId();
            if (bookingId == null || bookingId.isBlank()) {
                return Mono.just(Collections.emptyList());
            }
            return cargoTrackingServiceProxy.getTracking(bookingId)
                    .map(t -> Collections.singletonList(toCargoItem(t)))
                    .doOnError(t -> log.warn("Tracking service failed for bookingId={}: {}", bookingId, t.getMessage()))
                    .onErrorResume(t -> {
                        warnings.add("Cargo/tracking service unavailable");
                        return Mono.just(Collections.emptyList());
                    })
                    .defaultIfEmpty(Collections.emptyList());
        }
        // Fetch tracking for each container in parallel
        List<Mono<BookingSummaryResponse.CargoItem>> monos = containerIds.stream()
                .map(id -> cargoTrackingServiceProxy.getTracking(id)
                        .map(BookingSummaryHandler::toCargoItem)
                        .onErrorResume(t -> {
                            log.warn("Tracking failed for containerId={}: {}", id, t.getMessage());
                            return Mono.just(BookingSummaryResponse.CargoItem.builder()
                                    .trackingId(id)
                                    .status("UNAVAILABLE")
                                    .location(null)
                                    .lastUpdated(null)
                                    .build());
                        }))
                .collect(Collectors.toList());
        if (monos.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        return Mono.zip(monos, results -> {
            List<BookingSummaryResponse.CargoItem> list = new ArrayList<>();
            for (Object r : results) {
                list.add((BookingSummaryResponse.CargoItem) r);
            }
            return list;
        }).onErrorResume(t -> {
            warnings.add("Cargo/tracking service partially failed");
            return Mono.just(Collections.emptyList());
        });
    }

    private static BookingSummaryResponse.CargoItem toCargoItem(Tracking t) {
        return BookingSummaryResponse.CargoItem.builder()
                .trackingId(t.getId())
                .status(t.getStatus())
                .location(t.getLocation())
                .lastUpdated(t.getLastUpdated())
                .build();
    }

    private static BookingSummaryResponse.InvoiceSummary toInvoiceSummary(Invoice inv) {
        return BookingSummaryResponse.InvoiceSummary.builder()
                .status(inv.getStatus())
                .amount(inv.getAmount())
                .currency(inv.getCurrency())
                .build();
    }
}
