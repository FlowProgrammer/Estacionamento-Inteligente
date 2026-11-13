package br.com.smartpark.reservation;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class ReservationService {

    private static final Duration PENDING_TTL = Duration.ofMinutes(15);
    private static final Duration MIN_DURATION = Duration.ofMinutes(15);
    private static final Duration MAX_DURATION = Duration.ofHours(24);
    private static final Duration ARRIVAL_TOLERANCE = Duration.ofMinutes(5);
    private static final List<String> BLOCKING = List.of("PENDING_PAYMENT", "CONFIRMED", "CHECKED_IN");

    private final Firestore db;

    public ReservationService(Firestore db) {
        this.db = db;
    }

    public ReservationResponse create(String userId, CreateReservationRequest req) {
        Instant now = Instant.now();
        Instant arrival = req.arrivalExpected().toInstant();
        Instant departure = req.departureExpected().toInstant();
        validatePeriod(arrival, departure, now);

        DocumentReference lotRef = db.collection("parking_lots").document(req.parkingLotId());
        DocumentReference spaceRef = db.collection("spaces").document(req.spaceId());
        DocumentReference resRef = db.collection("reservations").document(); // ID gerado

        try {
            return db.runTransaction(tx -> {
                // ---- 1) TODAS as leituras primeiro (regra do Firestore) ----
                DocumentSnapshot lot = tx.get(lotRef).get();
                DocumentSnapshot space = tx.get(spaceRef).get();

                if (!lot.exists() || !Boolean.TRUE.equals(lot.getBoolean("active"))) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "lot_not_found",
                            "Estacionamento não encontrado");
                }
                if (!space.exists() || !Boolean.TRUE.equals(space.getBoolean("active"))
                        || !req.parkingLotId().equals(space.getString("parking_lot_id"))) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "space_not_found",
                            "Vaga não encontrada neste estacionamento");
                }

                Double rate = lot.getDouble("hourly_rate");
                long amountCents = estimateCents(rate, arrival, departure);

                // Desigualdade em um único campo (arrival_expected); o resto do overlap em Java
                Query conflicts = db.collection("reservations")
                        .whereEqualTo("space_id", req.spaceId())
                        .whereIn("status", BLOCKING)
                        .whereLessThan("arrival_expected", toTs(departure));

                for (DocumentSnapshot r : tx.get(conflicts).get().getDocuments()) {
                    Instant rDeparture = toInstant(r.getTimestamp("departure_expected"));
                    if (!rDeparture.isAfter(arrival)) continue;   // termina antes de começar

                    if ("PENDING_PAYMENT".equals(r.getString("status"))
                            && !toInstant(r.getTimestamp("expires_at")).isAfter(now)) {
                        continue;                                  // bloqueio expirado não conta
                    }
                    throw new ApiException(HttpStatus.CONFLICT, "conflict",
                            "Vaga indisponível neste período");
                }

                // ---- 2) Escritas ----
                // "Trava" por vaga: toda reserva da mesma vaga lê e escreve este documento,
                // então transações concorrentes são serializadas (sem condição de corrida).
                tx.update(spaceRef, "reservation_version", FieldValue.increment(1));

                Instant expiresAt = now.plus(PENDING_TTL);
                Map<String, Object> doc = new HashMap<>();
                doc.put("user_id", userId);
                doc.put("parking_lot_id", req.parkingLotId());
                doc.put("space_id", req.spaceId());
                doc.put("arrival_expected", toTs(arrival));
                doc.put("departure_expected", toTs(departure));
                doc.put("status", "PENDING_PAYMENT");
                doc.put("price_estimate_cents", amountCents);
                doc.put("currency", "BRL");
                doc.put("created_at", toTs(now));
                doc.put("expires_at", toTs(expiresAt));
                tx.create(resRef, doc);

                return new ReservationResponse(resRef.getId(), "PENDING_PAYMENT",
                        req.parkingLotId(), req.spaceId(), arrival, departure,
                        amountCents, "BRL", expiresAt);
            }).get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "busy", "Tente novamente");
        } catch (ExecutionException e) {
            ApiException business = findApiException(e);
            if (business != null) throw business;
            // Contenção alta (o SDK já tentou várias vezes) ou falha de infraestrutura
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "busy", "Tente novamente");
        }
    }

    // ---------- Validação e preço ----------
    private void validatePeriod(Instant arrival, Instant departure, Instant now) {
        if (!departure.isAfter(arrival)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_period",
                    "A saída deve ser posterior à chegada");
        }
        if (arrival.isBefore(now.minus(ARRIVAL_TOLERANCE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_period",
                    "A chegada não pode estar no passado");
        }
        Duration d = Duration.between(arrival, departure);
        if (d.compareTo(MIN_DURATION) < 0 || d.compareTo(MAX_DURATION) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_period",
                    "Duração fora do limite permitido");
        }
    }

    /** Política (Opção A): cobra por hora iniciada. Valor em centavos (inteiro). */
    private long estimateCents(Double hourlyRate, Instant arrival, Instant departure) {
        if (hourlyRate == null || hourlyRate <= 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "invalid_rate",
                    "Tarifa inválida para este local");
        }
        long seconds = Duration.between(arrival, departure).getSeconds();
        long hours = (seconds + 3599) / 3600; // arredonda para cima
        long rateCents = BigDecimal.valueOf(hourlyRate)
                .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return rateCents * hours;
    }

    // ---------- Utilitários ----------
    private static Timestamp toTs(Instant i) {
        return Timestamp.ofTimeSecondsAndNanos(i.getEpochSecond(), i.getNano());
    }

    private static Instant toInstant(Timestamp t) {
        return Instant.ofEpochSecond(t.getSeconds(), t.getNanos());
    }

    private static ApiException findApiException(Throwable t) {
        while (t != null) {
            if (t instanceof ApiException a) return a;
            t = t.getCause();
        }
        return null;
    }
}