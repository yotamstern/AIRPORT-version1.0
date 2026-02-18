package model.state;

import model.Flight;

/**
 * Represents the state where the flight is approaching the airport.
 * Active from 30 minutes before arrival until arrival time.
 * <p>
 * State Pattern: Encapsulates logic for the "Approaching" phase.
 * Transitions to {@link LandedState} when arrival time is reached.
 * </p>
 */
public class ApproachingState implements FlightState {

    @Override
    public void enter(Flight f) {
        System.out.println("Flight " + f.getFlightCode() + " entered Approaching State.");
    }

    @Override
    public void update(Flight f, int currentTime) {
        // Transition to LandedState when currentTime >= arrivalTime
        if (currentTime >= f.getArrivalTime()) {
            f.setState(new LandedState());
        }
    }
}
