package model;

import model.enums.PlaneType;
import model.state.FlightState;
import model.state.PlannedState;

/**
 * Represents a flight in the system.
 * Manages its own state via the State Pattern.
 * Implements Comparable to prioritize flights based on urgency score.
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

    /**
     * Constructs a new Flight.
     * Initializes state to {@link PlannedState}.
     * 
     * @param id           Unique identifier.
     * @param flightCode   Flight code (e.g., "UA123").
     * @param arrivalTime  Expected arrival time in minutes from midnight.
     * @param type         The distinct type of the plane.
     * @param urgencyScore Logic score for prioritization (Higher = Higher
     *                     priority).
     */
    public Flight(int id, String flightCode, int arrivalTime, PlaneType type, double urgencyScore) {
        this(id, flightCode, arrivalTime, type, urgencyScore, false);
    }

    public Flight(int id, String flightCode, int arrivalTime, PlaneType type, double urgencyScore,
            boolean isInternational) {
        this.id = id;
        this.flightCode = flightCode;
        this.arrivalTime = arrivalTime;
        this.type = type;
        this.urgencyScore = urgencyScore;
        this.isInternational = isInternational;
        this.state = new PlannedState(); // Initial state
        this.state.enter(this);
    }

    public boolean isInternational() {
        return isInternational;
    }

    public void setInternational(boolean isInternational) {
        this.isInternational = isInternational;
    }

    /**
     * Updates the flight's logic by delegating to the current state.
     * 
     * @param currentTime Current simulation time.
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

    /**
     * Calculates the scheduled departure time based on arrival and service
     * duration.
     * 
     * @return Departure time in minutes from midnight.
     */
    public int getDepartureTime() {
        return arrivalTime + serviceDuration;
    }

    /**
     * Compares flights based on urgency score for Min-Heap usage.
     * Note: Creating a Max-Heap effect if expected, but standard PriorityQueue is
     * Min-Heap.
     * Requirement: "Higher score = Higher priority".
     * Standard compareTo: (this < other) -> negative.
     * To make higher score come first in a priority queue (which usually polls
     * smallest), we might need to reverse logic OR user will use a max-heap.
     * "Logic: Compare urgencyScore (Higher score = Higher priority) to prepare for
     * the Min-Heap."
     * This phrasing is slightly ambiguous. Usually Min-Heap stores smallest element
     * at top.
     * If they want Higher Priority at top of a Min-Heap, they imply 'priority
     * value' is smaller for higher priority?
     * OR they assume we reverse the comparison so larger score = "smaller" in
     * comparison terms.
     * Re-reading: "Higher score = Higher priority".
     * If I want to bubble up the Higher Score in a standard tool, I should make it
     * "smaller".
     * So: other.score - this.score.
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
