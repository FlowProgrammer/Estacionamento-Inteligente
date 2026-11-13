package br.com.smartpark.iot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AccessRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,80}") String eventId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String reservationId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String deviceId,
        @NotBlank @Pattern(regexp = "ENTRY") String type   // EXIT entra numa próxima etapa
) {}