package model;

/**
 * Represents a group of passengers transferring from one flight to another.
 * Used by the Genetic Algorithm to minimize walking distance between connected
 * gates.
 */
public class Transfer {
    private int fromFlightId;
    private int toFlightId;
    private int numPassengers;

    public Transfer(int fromFlightId, int toFlightId, int numPassengers) {
        this.fromFlightId = fromFlightId;
        this.toFlightId = toFlightId;
        this.numPassengers = numPassengers;
    }

    public int getFromFlightId() {
        return fromFlightId;
    }

    public int getToFlightId() {
        return toFlightId;
    }

    public int getNumPassengers() {
        return numPassengers;
    }

    @Override
    public String toString() {
        return String.format("Transfer[from=%d, to=%d, pax=%d]", fromFlightId, toFlightId, numPassengers);
    }
}
