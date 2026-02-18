package model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Repository class to manage Flight entities.
 * Acts as an in-memory database using a HashMap.
 */
public class FlightRepository {
    private Map<Integer, Flight> flights;

    public FlightRepository() {
        this.flights = new HashMap<>();
    }

    /**
     * Adds a flight to the repository.
     * 
     * @param f The flight to add.
     */
    public void addFlight(Flight f) {
        flights.put(f.getId(), f);
    }

    /**
     * Retrieves a flight by its ID.
     * 
     * @param id The ID of the flight.
     * @return The Flight object, or null if not found.
     */
    public Flight getFlight(int id) {
        return flights.get(id);
    }

    /**
     * Retrieves all flights in the repository.
     * 
     * @return A collection of all flights.
     */
    public Collection<Flight> getAllFlights() {
        return flights.values();
    }
}
