package br.com.smartpark.parkinglot;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class ParkingLotService {

    private final Firestore firestore;

    public ParkingLotService(Firestore firestore) {
        this.firestore = firestore;
    }

    public List<ParkingLotDto> listActive() throws ExecutionException, InterruptedException {
        return firestore.collection("parking_lots")
                .whereEqualTo("active", true)
                .get().get()
                .getDocuments().stream()
                .map(this::toDto)
                .toList();
    }

    private ParkingLotDto toDto(QueryDocumentSnapshot d) {
        return new ParkingLotDto(
                d.getId(),
                d.getString("name"),
                d.getString("address"),
                d.getDouble("latitude"),
                d.getDouble("longitude"),
                d.getDouble("hourly_rate"),
                d.getString("opening_hours"));
    }
}