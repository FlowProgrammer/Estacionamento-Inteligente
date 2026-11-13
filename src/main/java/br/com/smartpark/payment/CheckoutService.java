package br.com.smartpark.payment;

import br.com.smartpark.reservation.ApiException;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.mercadopago.client.preference.*;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final Firestore db;
    private final String baseUrl;

    public CheckoutService(Firestore db, @Value("${smartpark.base-url}") String baseUrl) {
        this.db = db;
        this.baseUrl = baseUrl;
    }

    public String createCheckoutUrl(String uid, String reservationId)
            throws ExecutionException, InterruptedException {
        var ref = db.collection("reservations").document(reservationId);
        DocumentSnapshot r = ref.get().get();

        // 404 (e não 403) para não revelar que a reserva existe (T14)
        if (!r.exists() || !uid.equals(r.getString("user_id"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Reserva não encontrada");
        }
        if (!"PENDING_PAYMENT".equals(r.getString("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "invalid_state", "Reserva não aguarda pagamento");
        }
        Instant expiresAt = Instant.ofEpochSecond(r.getTimestamp("expires_at").getSeconds());
        if (!expiresAt.isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "expired", "Reserva expirada");
        }

        long cents = r.getLong("price_estimate_cents");   // valor do banco, nunca do cliente
        try {
            PreferenceRequest request = PreferenceRequest.builder()
                    .items(List.of(PreferenceItemRequest.builder()
                            .id(reservationId)
                            .title("Reserva SmartPark")
                            .quantity(1)
                            .currencyId("BRL")
                            .unitPrice(BigDecimal.valueOf(cents, 2))
                            .build()))
                    .externalReference(reservationId)
                    .notificationUrl(baseUrl + "/webhooks/mercadopago")
                    .backUrls(PreferenceBackUrlsRequest.builder()
                            .success(baseUrl + "/pagamento/sucesso")
                            .pending(baseUrl + "/pagamento/pendente")
                            .failure(baseUrl + "/pagamento/falha")
                            .build())
                    .autoReturn("approved")
                    .expires(true)
                    .expirationDateTo(OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                    .build();

            Preference pref = new PreferenceClient().create(request);
            ref.update("preference_id", pref.getId()).get();

            // Em teste use getSandboxInitPoint(); em produção, getInitPoint()
            return pref.getInitPoint();
        } catch (MPApiException e) {
            log.error("MP recusou a preferência: status={}", e.getStatusCode());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "payment_provider_error", "Falha no provedor");
        } catch (MPException e) {
            log.error("Falha ao criar preferência", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "payment_provider_error", "Falha no provedor");
        }
    }
}