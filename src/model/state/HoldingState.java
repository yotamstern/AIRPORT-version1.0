package model.state;

import model.Flight;

/**
 * The plane has landed but has no gate available — it circles or waits on the tarmac.
 * Every simulated minute the engine ({@link model.simulation.SimulationEngine#tryAssignHoldingFlights})
 * scans for a free compatible gate. As soon as one opens up, the flight transitions
 * to {@link AtGateState} and the wait is over.
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
