package br.com.smartpark.iot;

public record AccessResponse(boolean authorized, String reason) {}