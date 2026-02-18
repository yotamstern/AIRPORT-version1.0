package model.state;

import model.Flight;

/**
 * Represents the state where the flight is successfully parked at a gate.
 * <p>
 * State Pattern: Encapsulates logic for the "At Gate" phase.
 * </p>
 */
public class AtGateState implements FlightState {

    @Override
    public void enter(Flight f) {
        System.out.println("Flight " + f.getFlightCode() + " entered At Gate State.");
    }

    @Override
    public void update(Flight f, int currentTime) {
        // Flight is parked.
        // Could transition to a DepartedState in future phases if needed.
    }
}
