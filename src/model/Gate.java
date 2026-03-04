package model;

import model.enums.GateSize;
import model.enums.GateStatus;

/**
 * Represents a physical gate at the airport.
 * Holds information about its size, status, and location.
 */
public class Gate {
    private int id;
    private GateSize size;
    private GateStatus status;
    private int x;
    private int y;
    private boolean isInternational;

    /**
     * Constructs a new Gate.
     * 
     * @param id   The unique identifier for the gate.
     * @param size The size of the gate (SMALL, LARGE, JUMBO).
     * @param x    The x-coordinate location.
     * @param y    The y-coordinate location.
     */
    public Gate(int id, GateSize size, int x, int y) {
        this(id, size, x, y, false);
    }

    public Gate(int id, GateSize size, int x, int y, boolean isInternational) {
        this.id = id;
        this.size = size;
        this.status = GateStatus.FREE; // Default status
        this.x = x;
        this.y = y;
        this.isInternational = isInternational;
    }

    public boolean isInternational() {
        return isInternational;
    }

    public void setInternational(boolean isInternational) {
        this.isInternational = isInternational;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public GateSize getSize() {
        return size;
    }

    public void setSize(GateSize size) {
        this.size = size;
    }

    public GateStatus getStatus() {
        return status;
    }

    public void setStatus(GateStatus status) {
        this.status = status;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public String toString() {
        return "Gate{" +
                "id=" + id +
                ", size=" + size +
                ", status=" + status +
                ", x=" + x +
                ", y=" + y +
                '}';
    }
}
