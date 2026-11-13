package br.com.smartpark.payment;

import com.mercadopago.exceptions.MPApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookSignatureValidator validator;
    private final PaymentWebhookService service;

    public WebhookController(WebhookSignatureValidator validator, PaymentWebhookService service) {
        this.validator = validator;
        this.service = service;
    }

    @PostMapping("/webhooks/mercadopago")
    public ResponseEntity<Void> receive(
            @RequestParam Map<String, String> query,
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestBody(required = false) Map<String, Object> body) {

        String dataId = query.get("data.id");

        // 1) Origem: sem assinatura válida, nada é processado
        if (!validator.isValid(signature, requestId, dataId)) {
            return ResponseEntity.status(401).build();
        }

        // 2) Só eventos de pagamento
        String type = query.getOrDefault("type", body != null ? String.valueOf(body.get("type")) : "");
        if (!"payment".equals(type)) {
            return ResponseEntity.ok().build();
        }

        long paymentId;
        try {
            paymentId = Long.parseLong(dataId);
        } catch (NumberFormatException e) {
            return ResponseEntity.ok().build();
        }

        try {
            var outcome = service.process(paymentId);
            log.info("Webhook payment={} outcome={}", paymentId, outcome);
            // Fase 5: se CONFIRMED, enviar Telegram APÓS o commit (e com chave de idempotência)
            return ResponseEntity.ok().build();
        } catch (MPApiException e) {
            if (e.getStatusCode() == 404) {              // id inexistente (ex.: simulação do painel)
                return ResponseEntity.ok().build();
            }
            log.error("Erro MP ao consultar pagamento {}: status={}", paymentId, e.getStatusCode());
            return ResponseEntity.status(500).build();   // MP reenvia
        } catch (Exception e) {
            log.error("Falha ao processar pagamento {}", paymentId, e);
            return ResponseEntity.status(500).build();   // MP reenvia
        }
    }
}