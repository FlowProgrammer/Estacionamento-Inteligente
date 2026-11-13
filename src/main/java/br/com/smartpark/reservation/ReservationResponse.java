package br.com.smartpark.reservation;

import java.time.Instant;

public record ReservationResponse(
        String id,
        String status,
        String parkingLotId,
        String spaceId,
        Instant arrivalExpected,
        Instant departureExpected,
        long priceEstimateCents,
        String currency,
        Instant expiresAt
) {}