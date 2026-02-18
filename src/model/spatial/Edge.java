package model.spatial;

/**
 * Helper class representing an edge in the TerminalGraph.
 * Connects a source gate (implicit in adjacency list) to a target gate with a
 * specific weight.
 */
public class Edge {
    private int targetGateId;
    private double weight;

    /**
     * Constructs a new Edge.
     * 
     * @param targetGateId The ID of the destination gate.
     * @param weight       The cost (walking time/distance) to reach the target.
     */
    public Edge(int targetGateId, double weight) {
        this.targetGateId = targetGateId;
        this.weight = weight;
    }

    public int getTargetGateId() {
        return targetGateId;
    }

    public double getWeight() {
        return weight;
    }
}
