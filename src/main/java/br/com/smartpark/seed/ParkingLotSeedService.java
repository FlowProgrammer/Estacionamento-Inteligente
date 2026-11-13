package br.com.smartpark.seed;

import com.google.cloud.firestore.Firestore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@ConditionalOnProperty(name = "smartpark.seed.enabled", havingValue = "true")
public class ParkingLotSeedService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ParkingLotSeedService.class);
    private static final String COLLECTION = "parking_lots";

    private final Firestore firestore;

    public ParkingLotSeedService(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void run(ApplicationArguments args) throws ExecutionException, InterruptedException {
        record Seed(String id, Map<String, Object> data) {}

        List<Seed> seeds = List.of(
            new Seed("lot-centro", Map.of(
                "name", "SmartPark Unidade Centro (fictício)",
                "address", "Região central, ref. aproximada",
                "latitude", -23.5505,
                "longitude", -46.6333,
                "hourly_rate", 12.00,
                "opening_hours", "06:00-22:00",
                "active", true)),
            new Seed("lot-shopping", Map.of(
                "name", "SmartPark Unidade Shopping (fictício)",
                "address", "Zona oeste, ref. aproximada",
                "latitude", -23.5614,
                "longitude", -46.6559,
                "hourly_rate", 9.50,
                "opening_hours", "08:00-23:00",
                "active", true))
        );

        for (Seed s : seeds) {
            // ID fixo + set(): idempotente, reiniciar não duplica
            firestore.collection(COLLECTION).document(s.id()).set(s.data()).get();
            log.info("parking_lot '{}' garantido no Firestore", s.id());
        }
    }
}