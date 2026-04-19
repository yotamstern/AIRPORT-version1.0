package model;

import model.enums.PlaneType;
import model.state.FlightState;
import model.state.PlannedState;

/**
 * A flight moving through the airport. Owns its lifecycle state (Planned →
 * Approaching → Landed → AtGate → Departed) via the State pattern.
 * Implements Comparable so the urgency min-heap can order flights correctly.
 */
public class Flight implements Comparable<Flight> {
    private int id;
    private String flightCode;
    private int arrivalTime; // Minutes from midnight
    private int serviceDuration = 45; // Default turnaround time in minutes
    private PlaneType type;
    private double urgencyScore;
    private FlightState state;
    private Gate assignedGate; // Proactively added for UI system integration
    private boolean isInternational; // Phase 15 Constraints
    private String airlineCode; // Airline clustering constraint
    private int passengerCount; // Number of passengers on this flight

    /**
     * @param id           Unique flight ID.
     * @param flightCode   Human-readable code, e.g. "UA123".
     * @param arrivalTime  Expected arrival in minutes from midnight.
     * @param type         Plane size category.
     * @param urgencyScore Higher value = higher priority in the scheduling heap.
     */
    public Flight(int id, String flightCode, int arrivalTime, PlaneType type, double urgencyScore) {
        this(id, flightCode, arrivalTime, type, urgencyScore, false, "UA"); // Default UA
    }

    public Flight(int id, String flightCode, int arrivalTime, PlaneType type, double urgencyScore,
            boolean isInternational) {
        this(id, flightCode, arrivalTime, type, urgencyScore, isInternational, "UA"); // Default UA
    }

    public Flight(int id, String flightCode, int arrivalTime, PlaneType type, double urgencyScore,
            boolean isInternational, String airlineCode) {
        this.id = id;
        this.flightCode = flightCode;
        this.arrivalTime = arrivalTime;
        this.type = type;
        this.urgencyScore = urgencyScore;
        this.isInternational = isInternational;
        this.airlineCode = airlineCode;
        this.state = new PlannedState(); // Initial state
        this.state.enter(this);
    }

    public int getPassengerCount() {
        return passengerCount;
    }

    public void setPassengerCount(int passengerCount) {
        this.passengerCount = passengerCount;
    }

    public String getAirlineCode() {
        return airlineCode;
    }

    public boolean isInternational() {
        return isInternational;
    }

    public void setInternational(boolean isInternational) {
        this.isInternational = isInternational;
    }

    /**
     * Advances this flight's state for the current simulation tick.
     */
    public void update(int currentTime) {
        state.update(this, currentTime);
    }

    public void setState(FlightState state) {
        this.state = state;
        this.state.enter(this);
    }

    public FlightState getState() {
        return state;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFlightCode() {
        return flightCode;
    }

    public void setFlightCode(String flightCode) {
        this.flightCode = flightCode;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(int arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public PlaneType getType() {
        return type;
    }

    public void setType(PlaneType type) {
        this.type = type;
    }

    public double getUrgencyScore() {
        return urgencyScore;
    }

    public void setUrgencyScore(double urgencyScore) {
        this.urgencyScore = urgencyScore;
    }

    public int getServiceDuration() {
        return serviceDuration;
    }

    public void setServiceDuration(int serviceDuration) {
        this.serviceDuration = serviceDuration;
    }

    public Gate getAssignedGate() {
        return assignedGate;
    }

    public void setAssignedGate(Gate assignedGate) {
        this.assignedGate = assignedGate;
    }

    /** Departure time = arrival + service duration (both in minutes from midnight). */
    public int getDepartureTime() {
        return arrivalTime + serviceDuration;
    }

    /**
     * Reversed comparison so that higher urgency scores sort first in a standard
     * min-heap (i.e., higher score = "smaller" in comparison terms).
     */
    @Override
    public int compareTo(Flight other) {
        // Descending order of urgency score
        return Double.compare(other.urgencyScore, this.urgencyScore);
    }

    @Override
    public String toString() {
        return "Flight{" +
                "id=" + id +
                ", flightCode='" + flightCode + '\'' +
                ", arrivalTime=" + arrivalTime +
                ", type=" + type +
                ", urgencyScore=" + urgencyScore +
                ", state=" + state.getClass().getSimpleName() +
                '}';
    }
}
