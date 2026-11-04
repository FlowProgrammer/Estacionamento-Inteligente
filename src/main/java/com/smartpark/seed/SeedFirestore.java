package com.smartpark.seed;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.FieldValue;
import com.smartpark.config.FirebaseInitializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Script ISOLADO de carga inicial (dados fictícios).
 * Não é chamado pela API. Execute manualmente:
 *
 *   mvn -q compile exec:java -Dexec.mainClass=com.smartpark.seed.SeedFirestore
 *
 * Idempotente: usa IDs fixos, então rodar de novo apenas sobrescreve os mesmos documentos.
 * Valores monetários em CENTAVOS (inteiro) para evitar erro de ponto flutuante.
 */
public class SeedFirestore {

    public static void main(String[] args) throws Exception {
        if (!"development".equals(System.getenv("SEED_ENV"))) {
            System.err.println("Abortado: defina SEED_ENV=development (nunca rode seed em produção).");
            System.exit(1);
        }

        Firestore db = FirebaseInitializer.initFirestore();
        WriteBatch batch = db.batch();

        // ---- parking_lots ----
        batch.set(db.collection("parking_lots").document("lot-centro"),
                lot("Unidade Centro", "Região central (endereço fictício)",
                        -12.9714, -38.5108, 800));
        batch.set(db.collection("parking_lots").document("lot-barra"),
                lot("Unidade Barra", "Região da Barra (endereço fictício)",
                        -13.0105, -38.5330, 1000));

        // ---- spaces (2 por unidade) ----
        for (var e : List.of(
                Map.entry("lot-centro-A01", new String[]{"lot-centro", "A-01"}),
                Map.entry("lot-centro-A02", new String[]{"lot-centro", "A-02"}),
                Map.entry("lot-barra-B01",  new String[]{"lot-barra",  "B-01"}),
                Map.entry("lot-barra-B02",  new String[]{"lot-barra",  "B-02"}))) {
            batch.set(db.collection("spaces").document(e.getKey()),
                    space(e.getValue()[0], e.getValue()[1]));
        }

        batch.commit().get(); // bloqueia até confirmar
        System.out.println("Seed concluído: 2 parking_lots e 4 spaces.");
        System.exit(0); // encerra threads do SDK
    }

    private static Map<String, Object> lot(String name, String address,
                                           double lat, double lng, long hourlyRateCents) {
        Map<String, Object> hours = new LinkedHashMap<>();
        hours.put("open", "06:00");
        hours.put("close", "22:00");
        hours.put("timezone", "America/Bahia");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("address", address);
        m.put("latitude", lat);
        m.put("longitude", lng);
        m.put("hourly_rate_cents", hourlyRateCents);
        m.put("currency", "BRL");
        m.put("opening_hours", hours);
        m.put("active", true);
        m.put("created_at", FieldValue.serverTimestamp());
        return m;
    }

    private static Map<String, Object> space(String lotId, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parking_lot_id", lotId);
        m.put("label", label);
        m.put("active", true);
        m.put("occupancy_state", "FREE");
        m.put("created_at", FieldValue.serverTimestamp());
        return m;
    }
}
