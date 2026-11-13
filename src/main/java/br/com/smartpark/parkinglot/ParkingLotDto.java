package br.com.smartpark.parkinglot;

public record ParkingLotDto(
        String id,
        String name,
        String address,
        Double latitude,
        Double longitude,
        Double hourlyRate,
        String openingHours
) {}