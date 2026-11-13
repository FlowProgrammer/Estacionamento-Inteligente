package br.com.smartpark.payment;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class PaymentWebhookService {

    public enum Outcome { CONFIRMED, DUPLICATE, RECORDED, NEEDS_RECONCILIATION, MISMATCH, IGNORED }

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);
    private final Firestore db;

    public PaymentWebhookService(Firestore db) {
        this.db = db;
    }

    /** Lança MPException/MPApiException em falha temporária: o controller responde 500 e o MP reenvia. */
    public Outcome process(long paymentId)
            throws MPException, MPApiException, ExecutionException, InterruptedException {

        // 1) Fonte da verdade: consulta direta à API do Mercado Pago
        Payment p = new PaymentClient().get(paymentId);
        String reservationId = p.getExternalReference();
        if (reservationId == null || reservationId.isBlank() || reservationId.contains("/")) {
            log.warn("Pagamento {} sem external_reference válido", paymentId);
            return Outcome.IGNORED;
        }
        String mpStatus = p.getStatus();
        BigDecimal paid = p.getTransactionAmount();
        String currency = p.getCurrencyId();

        DocumentReference payRef = db.collection("payments").document(String.valueOf(paymentId));
        DocumentReference resRef = db.collection("reservations").document(reservationId);

        // 2) Transação: leituras primeiro, depois escritas
        return db.runTransaction(tx -> {
            DocumentSnapshot pay = tx.get(payRef).get();
            DocumentSnapshot res = tx.get(resRef).get();

            // IDEMPOTÊNCIA: este pagamento aprovado já foi processado
            if (pay.exists() && Boolean.TRUE.equals(pay.getBoolean("processed"))) {
                return Outcome.DUPLICATE;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("reservation_id", reservationId);
            data.put("provider", "mercadopago");
            data.put("payment_id", String.valueOf(paymentId));
            data.put("status", mpStatus);
            data.put("amount", paid == null ? null : paid.toPlainString());
            data.put("currency", currency);
            data.put("verified_at", Timestamp.now());

            if (!res.exists()) {
                tx.set(payRef, data);
                return Outcome.IGNORED;
            }
            if (!"approved".equals(mpStatus)) {
                tx.set(payRef, data);               // pendente/rejeitado: só registra
                return Outcome.RECORDED;
            }

            data.put("processed", true);            // a partir daqui, repetições são no-op

            BigDecimal expected = BigDecimal.valueOf(res.getLong("price_estimate_cents"), 2);
            if (paid == null || paid.compareTo(expected) != 0 || !"BRL".equals(currency)) {
                data.put("reconciliation", "AMOUNT_MISMATCH");
                tx.set(payRef, data);
                return Outcome.MISMATCH;
            }

            String status = res.getString("status");
            boolean stillValid = "PENDING_PAYMENT".equals(status)
                    && res.getTimestamp("expires_at").compareTo(Timestamp.now()) > 0;

            if (stillValid) {
                tx.update(resRef,
                        "status", "CONFIRMED",
                        "payment_id", String.valueOf(paymentId),
                        "confirmed_at", FieldValue.serverTimestamp());
                tx.set(payRef, data);
                return Outcome.CONFIRMED;
            }

            // Aprovado, mas a reserva expirou/foi cancelada (ou já tem outro pagamento):
            // NÃO confirma nem abre cancela. Fica marcado para reconciliação/estorno.
            data.put("reconciliation", "NEEDS_REVIEW");
            tx.set(payRef, data);
            return Outcome.NEEDS_RECONCILIATION;
        }).get();
    }
}