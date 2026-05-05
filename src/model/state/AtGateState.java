package model.state;

import model.Flight;
import model.enums.GateStatus;

/**
 * The plane is parked and passengers are boarding or disembarking.
 * When the scheduled departure time arrives, the gate is freed and the flight
 * moves to {@link DepartedState} — its final, terminal state.
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
        if (currentTime >= f.getDepartureTime()){
            if(f.getAssignedGate() != null) {
                f.getAssignedGate().setStatus(GateStatus.FREE);
                f.setAssignedGate(null); // Free the reference so the Gantt removes the block
            }
            f.setState(new DepartedState());
        }
    }
}
