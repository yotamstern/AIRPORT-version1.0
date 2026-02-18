package model.state;

import model.Flight;

/**
 * Represents the state where the flight has just landed.
 * This is a critical decision point for gate assignment.
 * <p>
 * State Pattern: Encapsulates logic for the "Landed" phase.
 * Future logic will handle gate assignment here.
 * </p>
 */
public class LandedState implements FlightState {

    @Override
    public void enter(Flight f) {
        System.out.println("Flight " + f.getFlightCode() + " entered Landed State.");
    }

    @Override
    public void update(Flight f, int currentTime) {
        // Critical decision point.
        // Logic for assigning a gate or moving to HoldingState would go here.
        // For Phase 1, we might just stay here or manually transition for testing.
    }
}
