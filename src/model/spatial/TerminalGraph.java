package model.spatial;

import model.Gate;
import model.enums.GateSize; // Needed for dummy gates in main

import java.util.*;

/**
 * Represents the layout of the airport terminal as a graph.
 * Uses an Adjacency List to store connections between gates.
 * Implements the Floyd-Warshall Algorithm to calculate shortest walking times.
 *
 * <p>
 * <b>Space Complexity:</b> O(V^2) for distance matrix.
 * The adjacency list stores each gate once and each connection twice
 * (undirected).
 * </p>
 */
public class TerminalGraph {
    // Adjacency List: Gate ID -> List of Edges
    private Map<Integer, List<Edge>> adjList;

    private double[][] distanceMatrix;

    public TerminalGraph() {
        this.adjList = new HashMap<>();
    }

    /**
     * Initializes the graph entry for a specific gate.
     * 
     * @param g The gate to add.
     */
    public void addGate(Gate g) {
        adjList.putIfAbsent(g.getId(), new ArrayList<>());
    }

    /**
     * Connects two gates with a bidirectional edge.
     * The weight is calculated using the Euclidean distance between their
     * coordinates.
     *
     * <p>
     * <b>Euclidean Distance Calculation:</b>
     * We use the formula d = sqrt((x2 - x1)^2 + (y2 - y1)^2) to simulate the
     * straight-line
     * walking distance (or time) between two gates. This assumes an open plan
     * terminal.
     * </p>
     *
     * @param g1 The first gate.
     * @param g2 The second gate.
     */
    public void connectGates(Gate g1, Gate g2) {
        double weight = Math.hypot(g1.getX() - g2.getX(), g1.getY() - g2.getY());

        // Add edge g1 -> g2
        adjList.get(g1.getId()).add(new Edge(g2.getId(), weight));
        // Add edge g2 -> g1 (Undirected)
        adjList.get(g2.getId()).add(new Edge(g1.getId(), weight));
    }

    /**
     * Retrieves the IDs of all neighboring gates.
     * Required for Breadth-First Search (BFS) algorithms.
     *
     * @param gateId The ID of the central gate.
     * @return A list of connected gate IDs.
     */
    public List<Integer> getNeighbors(int gateId) {
        List<Integer> neighbors = new ArrayList<>();
        List<Edge> edges = adjList.get(gateId);
        if (edges != null) {
            for (Edge edge : edges) {
                neighbors.add(edge.getTargetGateId());
            }
        }
        return neighbors;
    }

    public void initializeDistanceMatrix() {
        int maxGateId = 0;
        for (Integer id : adjList.keySet()) {
            if (id > maxGateId) {
                maxGateId = id;
            }
        }

        int n = maxGateId + 1;
        distanceMatrix = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    distanceMatrix[i][j] = 0.0;
                } else {
                    distanceMatrix[i][j] = Double.POSITIVE_INFINITY;
                }
            }
        }

        for (Map.Entry<Integer, List<Edge>> entry : adjList.entrySet()) {
            int u = entry.getKey();
            for (Edge edge : entry.getValue()) {
                int v = edge.getTargetGateId();
                if (u < n && v < n) {
                    distanceMatrix[u][v] = edge.getWeight();
                }
            }
        }

        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (distanceMatrix[i][j] > distanceMatrix[i][k] + distanceMatrix[k][j]) {
                        distanceMatrix[i][j] = distanceMatrix[i][k] + distanceMatrix[k][j];
                    }
                }
            }
        }
    }

    public double getShortestDistance(int arrivalGateId, int departureGateId) {
        return distanceMatrix[arrivalGateId][departureGateId];
    }

    /**
     * Test Harness Main Method.
     * Verifies the graph construction and Dijkstra's algorithm.
     */
    public static void main(String[] args) {
        TerminalGraph graph = new TerminalGraph();

        // Create dummy gates
        // A (0,0) --- 10 --- B (10,0)
        // | |
        // 5 5
        // | |
        // C (0,5) --- 10 --- D (10,5)

        Gate g1 = new Gate(1, GateSize.SIZE_SMALL, 0, 0); // A
        Gate g2 = new Gate(2, GateSize.SIZE_SMALL, 10, 0); // B
        Gate g3 = new Gate(3, GateSize.SIZE_SMALL, 0, 5); // C
        Gate g4 = new Gate(4, GateSize.SIZE_SMALL, 10, 5); // D

        // Add gates
        graph.addGate(g1);
        graph.addGate(g2);
        graph.addGate(g3);
        graph.addGate(g4);

        // Connect gates
        // Path A->B is 10.0
        graph.connectGates(g1, g2);
        // Path A->C is 5.0
        graph.connectGates(g1, g3);
        // Path B->D is 5.0
        graph.connectGates(g2, g4);
        // Path C->D is 10.0
        graph.connectGates(g3, g4);

        System.out.println("Graph Test Harness Started...");

        graph.initializeDistanceMatrix();

        // Test 1: Direct connection A -> B
        double dist1 = graph.getShortestDistance(1, 2);
        System.out.println("Distance Gate 1 -> Gate 2 (Expected 10.0): " + dist1);

        // Test 2: Indirect connection A -> C -> D (5+10 = 15) vs A -> B -> D (10+5 =
        // 15)
        double dist2 = graph.getShortestDistance(1, 4);
        System.out.println("Distance Gate 1 -> Gate 4 (Expected 15.0): " + dist2);

        double dist4 = graph.getShortestDistance(2, 4);
        System.out.println("Distance Gate 2 -> Gate 4: " + dist4);

        // Add a shortcut diagonal A -> D
        // sqrt(10^2 + 5^2) = sqrt(125) approx 11.18
        graph.connectGates(g1, g4);
        graph.initializeDistanceMatrix(); // Re-calculate to pick up new edges
        double dist3 = graph.getShortestDistance(1, 4);
        System.out.println("Distance Gate 1 -> Gate 4 with Diagonal (Expected ~11.18): " + dist3);

        if (Math.abs(dist1 - 10.0) < 0.001 && Math.abs(dist3 - 11.1803) < 0.001) {
            System.out.println("SUCCESS: Floyd-Warshall algorithm verified.");
        } else {
            System.out.println("FAILURE: Calculations incorrect.");
        }
    }
}
