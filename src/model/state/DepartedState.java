package model.state;
import model.Flight;

/**
 * The flight has left the gate and is gone. This is the terminal state —
 * once a flight departs it no longer participates in any simulation logic.
 */
public class DepartedState implements FlightState {

    @Override
    public void enter(Flight f) {
        System.out.println("Flight " + f.getFlightCode() + " has departed.");
    }

    @Override
    public void update(Flight f, int currentTime) {
        // Nothing to do — departed flights don't transition further
    }
}
