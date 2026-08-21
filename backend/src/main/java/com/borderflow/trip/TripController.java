package com.borderflow.trip;

import com.borderflow.common.TripNotFoundException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * Read-only trip endpoints. Every field returned here comes from THIS
 * site's own local Postgres -- there is no cross-site network call
 * involved, because replication has already brought every other site's
 * State fragment writes into this database. That's the whole point of
 * the multi-leader design: a site can answer "where is this trip right
 * now" correctly even if it currently has zero connectivity to the site
 * that's physically holding the trip.
 */
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripMasterRepository tripMasterRepository;
    private final TripStateRepository tripStateRepository;

    public TripController(TripMasterRepository tripMasterRepository, TripStateRepository tripStateRepository) {
        this.tripMasterRepository = tripMasterRepository;
        this.tripStateRepository = tripStateRepository;
    }

    @GetMapping
    public List<TripSummaryResponse> listTrips() {
        return tripMasterRepository.findAll().stream()
                .map(master -> tripStateRepository.findById(master.getTripId())
                        .map(state -> TripSummaryResponse.from(master, state))
                        .orElse(null))
                .filter(t -> t != null)
                .toList();
    }

    @GetMapping("/{tripId}")
    public TripSummaryResponse getTrip(@PathVariable UUID tripId) {
        TripMaster master = tripMasterRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
        TripState state = tripStateRepository.findById(tripId)
                .orElseThrow(() -> new TripNotFoundException(tripId));
        return TripSummaryResponse.from(master, state);
    }
}
