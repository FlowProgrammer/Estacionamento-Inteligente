package br.com.smartpark.payment;

import br.com.smartpark.security.FirebaseAuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
public class CheckoutController {

    private final CheckoutService service;
    private final FirebaseAuthService auth;

    public CheckoutController(CheckoutService service, FirebaseAuthService auth) {
        this.service = service;
        this.auth = auth;
    }

    @PostMapping("/api/reservations/{id}/checkout")
    public Map<String, String> checkout(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization)
            throws ExecutionException, InterruptedException {
        String uid = auth.requireUid(authorization);
        return Map.of("checkoutUrl", service.createCheckoutUrl(uid, id));
    }
}