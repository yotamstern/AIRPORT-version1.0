package model.state;

import model.Flight;

/**
 * The plane has touched down. This is the critical fork in the road:
 * if a gate was pre-assigned by the GA, the flight moves straight to {@link AtGateState};
 * otherwise it enters {@link HoldingState} and waits for a gate to open up.
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
