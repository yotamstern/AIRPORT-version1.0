package model.state;

import model.Flight;

/**
 * Represents the state where the flight is holding/waiting for a gate.
 * This handles error or overflow scenarios.
 * <p>
 * State Pattern: Encapsulates logic for the "Holding" phase.
 * </p>
 */
public class HoldingState implements FlightState {

    @Override
    public void enter(Flight f) {
        System.out.println("Flight " + f.getFlightCode() + " entered Holding State.");
    }

    @Override
    public void update(Flight f, int currentTime) {
        if (f.getAssignedGate() != null)
            f.setState(new AtGateState());
    }
}
