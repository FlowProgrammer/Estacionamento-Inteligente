package br.com.smartpark.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class WebhookSignatureValidator {

    private final byte[] secret;

    public WebhookSignatureValidator(@Value("${mercadopago.webhook-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(String xSignature, String xRequestId, String dataId) {
        if (xSignature == null || dataId == null) return false;

        String ts = null, v1 = null;
        for (String part : xSignature.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("ts")) ts = kv[1];
            else if (kv[0].equals("v1")) v1 = kv[1];
        }
        if (ts == null || v1 == null) return false;

        StringBuilder manifest = new StringBuilder("id:").append(dataId.toLowerCase()).append(';');
        if (xRequestId != null) manifest.append("request-id:").append(xRequestId).append(';');
        manifest.append("ts:").append(ts).append(';');

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String expected = HexFormat.of().formatHex(
                    mac.doFinal(manifest.toString().getBytes(StandardCharsets.UTF_8)));
            // comparação em tempo constante
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}