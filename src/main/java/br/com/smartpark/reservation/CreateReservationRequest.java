package br.com.smartpark.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;

// Sem campos de preço ou usuário: o cliente não manda isso (T13)
public record CreateReservationRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String parkingLotId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String spaceId,
        @NotNull OffsetDateTime arrivalExpected,    // exige fuso: 2026-11-12T14:00:00-03:00
        @NotNull OffsetDateTime departureExpected
) {}