package model.state;

import model.Flight;

/**
 * The flight is in the air and closing in on the airport — within 30 minutes of landing.
 * The gate assignment is already set at this point; this state just waits for the
 * clock to reach the arrival time before handing off to {@link LandedState}.
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
