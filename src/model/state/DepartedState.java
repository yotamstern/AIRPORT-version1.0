package model.state;
import model.Flight;

public class DepartedState implements FlightState {

    @Override
    public void enter(Flight f) {
        System.out.println("Flight " + f.getFlightCode() + " has departed.");
    }

    @Override
    public void update(Flight f, int currentTime) {
        // Terminal state — no transitions
    }
}
