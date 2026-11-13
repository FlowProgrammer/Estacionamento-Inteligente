package br.com.smartpark.parkinglot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/parking-lots")
public class ParkingLotController {

    private static final Logger log = LoggerFactory.getLogger(ParkingLotController.class);

    private final ParkingLotService service;

    public ParkingLotController(ParkingLotService service) {
        this.service = service;
    }

    @GetMapping
    public List<ParkingLotDto> list() {
        try {
            return service.listActive();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        } catch (ExecutionException e) {
            log.error("Falha ao listar estacionamentos", e);
            // Sem detalhes internos na resposta
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}