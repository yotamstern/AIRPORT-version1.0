package model.state;

import model.Flight;

/**
 * The first state in a flight's lifecycle — the flight is on the books but still
 * far from the airport. Once the simulated clock reaches 30 minutes before the
 * scheduled arrival, the flight transitions to {@link ApproachingState}.
 */
public class PlannedState implements FlightState {

    private static final int APPROACHING_THRESHOLD_MINUTES = 30;

    @Override
    public void enter(Flight f) {
        System.out.println("Flight " + f.getFlightCode() + " entered Planned State.");
    }

    @Override
    public void update(Flight f, int currentTime) {
        if (currentTime >= f.getArrivalTime() - APPROACHING_THRESHOLD_MINUTES) {
            f.setState(new ApproachingState());
        }
    }
}
