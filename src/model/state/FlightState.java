package model.state;

import model.Flight;

/**
 * Interface representing the state of a flight in the system.
 * Part of the State Pattern implementation.
 * <p>
 * The State Pattern is used here to manage the flight lifecycle without complex
 * if-else chains.
 * It allows the Flight object to alter its behavior when its internal state
 * changes,
 * adhering to the Open/Closed Principle and making state transitions explicit
 * and manageable.
 * </p>
 */
public interface FlightState {
    /**
     * Called when a flight enters this state.
     * 
     * @param f The flight entering the state.
     */
    void enter(Flight f);

    /**
     * Updates the flight logic based on the current time.
     * Handles transitions to the next state.
     * 
     * @param f           The flight to update.
     * @param currentTime The current simulation time (minutes from midnight).
     */
    void update(Flight f, int currentTime);
}
