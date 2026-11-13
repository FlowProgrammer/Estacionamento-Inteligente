package br.com.smartpark.iot;

import br.com.smartpark.reservation.ApiException;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Service
public class AccessService {

    private static final Logger log = LoggerFactory.getLogger(AccessService.class);
    private static final Duration EARLY_TOLERANCE = Duration.ofMinutes(15);

    private final Firestore db;

    public AccessService(Firestore db) { this.db = db; }

    public AccessResponse decide(AccessRequest req) {
        Instant now = Instant.now();
        var evRef  = db.collection("access_events").document(req.eventId());
        var devRef = db.collection("devices").document(req.deviceId());
        var resRef = db.collection("reservations").document(req.reservationId());

        try {
            return db.runTransaction(tx -> {
                DocumentSnapshot ev  = tx.get(evRef).get();
                DocumentSnapshot dev = tx.get(devRef).get();
                DocumentSnapshot res = tx.get(resRef).get();

                // IDEMPOTÊNCIA: mesmo event_id devolve a mesma resposta, sem novo efeito
                if (ev.exists()) return answer("GRANTED".equals(ev.getString("result")));

                String denyReason = evaluate(dev, res, now);   // null = autorizado
                boolean ok = denyReason == null;

                Map<String, Object> e = new HashMap<>();
                e.put("event_id", req.eventId());
                e.put("reservation_id", req.reservationId());
                e.put("device_id", req.deviceId());
                e.put("type", req.type());
                e.put("timestamp", FieldValue.serverTimestamp());
                e.put("result", ok ? "GRANTED" : "DENIED");
                e.put("reason", ok ? "OK" : denyReason);   // motivo detalhado só no banco
                tx.create(evRef, e);

                if (ok) {
                    tx.update(resRef, "status", "CHECKED_IN",
                            "checkin_at", FieldValue.serverTimestamp());
                }
                return answer(ok);
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "busy", "Tente novamente");
        } catch (ExecutionException e) {
            log.error("Falha ao decidir acesso", e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "busy", "Tente novamente");
        }
    }

    private String evaluate(DocumentSnapshot dev, DocumentSnapshot res, Instant now) {
        if (!dev.exists() || !"ACTIVE".equals(dev.getString("status"))) return "DEVICE_INACTIVE";
        if (!res.exists()) return "RESERVATION_NOT_FOUND";
        if (!"CONFIRMED".equals(res.getString("status"))) return "NOT_CONFIRMED"; // pendente não entra
        if (!Objects.equals(dev.getString("parking_lot_id"), res.getString("parking_lot_id")))
            return "WRONG_LOT";

        Instant arrival   = toInstant(res.getTimestamp("arrival_expected"));
        Instant departure = toInstant(res.getTimestamp("departure_expected"));
        if (now.isBefore(arrival.minus(EARLY_TOLERANCE)) || now.isAfter(departure))
            return "OUTSIDE_WINDOW";
        return null;
    }

    // Resposta mínima e genérica para o dispositivo
    private AccessResponse answer(boolean ok) {
        return new AccessResponse(ok, ok ? "OK" : "DENIED");
    }

    private static Instant toInstant(Timestamp t) {
        return Instant.ofEpochSecond(t.getSeconds(), t.getNanos());
    }
}