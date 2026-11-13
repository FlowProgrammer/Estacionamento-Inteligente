package br.com.smartpark.security;

import br.com.smartpark.reservation.ApiException;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FirebaseAuthService {

    /** Retorna o uid do usuário. O uid nunca vem do corpo da requisição. */
    public String requireUid(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Não autenticado");
        }
        try {
            return FirebaseAuth.getInstance()
                    .verifyIdToken(authorizationHeader.substring(7)).getUid();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Token inválido");
        }
    }
}