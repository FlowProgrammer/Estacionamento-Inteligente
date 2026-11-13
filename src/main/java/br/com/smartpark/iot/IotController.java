package br.com.smartpark.iot;

import br.com.smartpark.reservation.ApiException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class IotController {

    private final AccessService service;
    private final byte[] deviceSecret;

    public IotController(AccessService service,
                         @Value("${smartpark.device-secret}") String secret) {
        this.service = service;
        this.deviceSecret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/api/iot/access-request")
    public AccessResponse accessRequest(
            @RequestHeader(value = "X-Device-Key", required = false) String key,
            @Valid @RequestBody AccessRequest body) {
        if (key == null || !MessageDigest.isEqual(deviceSecret, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Dispositivo não autenticado");
        }
        return service.decide(body);
    }
}