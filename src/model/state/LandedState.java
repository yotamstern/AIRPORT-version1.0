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
        if (f.getAssignedGate() != null)
            f.setState(new AtGateState());
        else
            f.setState(new HoldingState());
    }
}
