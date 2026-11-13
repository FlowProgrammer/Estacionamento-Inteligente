package br.com.smartpark.reservation;

import br.com.smartpark.security.FirebaseAuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService service;
    private final FirebaseAuthService auth;

    public ReservationController(ReservationService service, FirebaseAuthService auth) {
        this.service = service;
        this.auth = auth;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateReservationRequest body) {
        String uid = auth.requireUid(authorization);
        return service.create(uid, body);
    }
}