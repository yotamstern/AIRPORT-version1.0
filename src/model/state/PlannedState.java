package model.state;

import model.Flight;

/**
 * Represents the initial state of a flight (Planned).
 * The flight is scheduled but has not yet approached the airport.
 * <p>
 * State Pattern: Encapsulates logic for the "Planned" phase.
 * Transitions to {@link ApproachingState} when within 30 minutes of arrival.
 * </p>
 */
public class PlannedState implements FlightState {

    @Override
    public void enter(Flight f) {
        System.out.println("Flight " + f.getFlightCode() + " entered Planned State.");
    }

    @Override
    public void update(Flight f, int currentTime) {
        // Transition to ApproachingState when currentTime >= arrivalTime - 30
        if (currentTime >= f.getArrivalTime() - 30) {
            f.setState(new ApproachingState());
        }
    }
}
