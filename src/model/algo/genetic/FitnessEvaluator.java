package model.algo.genetic;

import model.Flight;
import model.FlightRepository;
import model.Gate;
import model.Transfer;
import model.enums.GateSize;
import model.enums.PlaneType;
import model.spatial.TerminalGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Component responsible for calculating the fitness score of a schedule.
 * Fitness guides the evolution process.
 */
public class FitnessEvaluator {
    private TerminalGraph graph;
    private FlightRepository repo;
    private Map<Integer, Gate> gateMap;

    // Constants
    private static final double REWARD_VALID_FLIGHT = 10000.0;
    private static final double HARD_PENALTY_OVERLAP = 15000.0;
    private static final double HARD_PENALTY_SIZE = 50000.0;
    private static final double HARD_PENALTY_INT = 50000.0;
    private static final double SOFT_PENALTY_WALK = 0.1;// 0.1
    private static final double SOFT_PENALTY_BUFFER = 50.0;// 50.0
    private static final double SOFT_PENALTY_TRANSFER = 0.1;
    private static final double SOFT_PENALTY_WASTE = 2000.0;

    public FitnessEvaluator(TerminalGraph graph, FlightRepository repo, List<Gate> gates) {
        this.graph = graph;
        this.repo = repo;
        this.gateMap = new HashMap<>();
        if (gates != null) {
            for (Gate g : gates) {
                this.gateMap.put(g.getId(), g);
            }
        }
    }

    /**
     * Helper method to calculate how much a plane is wasting gate size capacity.
     */
    public int getWastedSpaceLevel(GateSize gateSize, PlaneType planeType) {
        if (planeType == PlaneType.SMALL_BODY) {
            if (gateSize == GateSize.SIZE_LARGE) return 1;
            if (gateSize == GateSize.SIZE_JUMBO) return 2;
        } else if (planeType == PlaneType.LARGE_BODY) {
            if (gateSize == GateSize.SIZE_JUMBO) return 1;
        }
        return 0;
    }

    /**
     * Breadth-First Search (BFS) to count disconnected clusters of planes.
     * Used for grouping flights from the same airline together physically.
     */
    private int countConnectedComponents(List<Integer> activeGates, TerminalGraph graph) {
        Set<Integer> targetGates = new HashSet<>(activeGates);
        Set<Integer> visited = new HashSet<>();
        int componentCount = 0;

        for (Integer startGate : targetGates) {
            if (!visited.contains(startGate)) {
                componentCount++;
                Queue<Integer> queue = new LinkedList<>();
                queue.add(startGate);
                visited.add(startGate);

                while (!queue.isEmpty()) {
                    int currentGate = queue.poll();
                    
                    for (Integer neighbor : graph.getNeighbors(currentGate)) {
                        if (targetGates.contains(neighbor) && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return componentCount;
    }

    /**
     * Helper method to check if a Gate is large enough for a PlaneType.
     * Task 1: Hard Penalty 2 logic helper.
     */
    public boolean isGateLargeEnough(GateSize gateSize, PlaneType planeType) {
        if (planeType == PlaneType.SMALL_BODY)
            return true; // fits anywhere
        if (planeType == PlaneType.LARGE_BODY)
            return gateSize == GateSize.SIZE_LARGE || gateSize == GateSize.SIZE_JUMBO;
        if (planeType == PlaneType.JUMBO_BODY)
            return gateSize == GateSize.SIZE_JUMBO;
        return false;
    }

    /**
     * Calculates the fitness of a chromosome (schedule).
     * 
     * Formula: Fitness = BaseScore - HardPenalties - SoftPenalties
     * 
     * @param chromosome The schedule to evaluate.
     * @param flights    The list of flights.
     * @return The fitness score.
     */
    public double calculateFitness(int[] chromosome, List<Flight> flights) {
        double hardPenalties = 0;
        double softPenalties = 0;
        double reward = 0;

        // Group flights by assigned gate
        Map<Integer, List<Flight>> gateAssignments = new HashMap<>();
        Map<Integer, Integer> flightIdToGateMap = new HashMap<>();

        for (int i = 0; i < chromosome.length; i++) {
            int gateId = chromosome[i];
            Flight f = flights.get(i);

            gateAssignments.putIfAbsent(gateId, new ArrayList<>());
            gateAssignments.get(gateId).add(f);
            
            flightIdToGateMap.put(f.getId(), gateId);

            // Hard Penalty 2: Size Mismatch (-50000 points)
            Gate gate = gateMap.get(gateId);
            boolean isValidSize = gate != null && isGateLargeEnough(gate.getSize(), f.getType());
            boolean isValidInt = gate != null && f.isInternational() == gate.isInternational();

            if (!isValidSize) {
                hardPenalties += HARD_PENALTY_SIZE;
            }

            // Hard Penalty 3: Domestic/International mismatch (-50000 points)
            if (!isValidInt) {
                hardPenalties += HARD_PENALTY_INT;
            }

            if (isValidSize && isValidInt) {
                reward += REWARD_VALID_FLIGHT;
                softPenalties += getWastedSpaceLevel(gate.getSize(), f.getType()) * SOFT_PENALTY_WASTE;
            }
        }

        // Check for Time Overlaps and Buffer Time per gate
        for (Map.Entry<Integer, List<Flight>> entry : gateAssignments.entrySet()) {
            List<Flight> assignedFlights = entry.getValue();
            // Sort flights by arrival time to properly check for overlaps and consecutive
            // spacing
            assignedFlights.sort(Comparator.comparingInt(Flight::getArrivalTime));

            // Check for Time Overlaps and Buffer Time per gate (O(N log N) Approach)
            for (int i = 0; i < assignedFlights.size() - 1; i++) {
                Flight f1 = assignedFlights.get(i);
                Flight fNext = assignedFlights.get(i + 1);

                // Hard Penalty 1: Time Overlap (-5000 points)
                // Since it's sorted by arrival time, fNext arrivals always occur at or after f1
                // arrivals.
                // We only have an overlap if fNext arrives BEFORE f1 departs.
                if (fNext.getArrivalTime() < f1.getDepartureTime()) {
                    hardPenalties += HARD_PENALTY_OVERLAP;
                } else {
                    // Soft Penalty 2: Buffer Time (-50 points if less than 15 mins for consecutive
                    // flights)
                    // If they don't overlap, check the buffer gap.
                    int buffer = fNext.getArrivalTime() - f1.getDepartureTime();
                    if (buffer < 15) {
                        softPenalties += SOFT_PENALTY_BUFFER;
                    }
                }
            }
        }

        // Soft Penalty 1: Walking Distance (-0.1 per point of distance)
        double totalWalkingDistance = 0;
        for (int gateId : chromosome) {
            double dist = graph.getShortestDistance(1, gateId); // Distance from Entrance (Gate 1)
            if (dist != Double.POSITIVE_INFINITY) {
                totalWalkingDistance += dist;
            } else {
                totalWalkingDistance += 1000; // Large penalty for unreachable gate
            }
        }
        softPenalties += totalWalkingDistance * SOFT_PENALTY_WALK;

        // Soft Penalty: Transfer Walking Distance
        double totalTransferDistance = 0;
        if (repo.getAllTransfers() != null) {
            for (Transfer transfer : repo.getAllTransfers()) {
                Integer fromGateId = flightIdToGateMap.get(transfer.getFromFlightId());
                Integer toGateId = flightIdToGateMap.get(transfer.getToFlightId());
                
                if (fromGateId != null && toGateId != null) {
                    double dist = graph.getShortestDistance(fromGateId, toGateId);
                    if (dist != Double.POSITIVE_INFINITY) {
                        // Weight the distance by the number of transferring passengers
                        totalTransferDistance += dist * transfer.getNumPassengers();
                    } else {
                        totalTransferDistance += 1000 * transfer.getNumPassengers(); // Unreachable penalty
                    }
                }
            }
        }
        softPenalties += totalTransferDistance * SOFT_PENALTY_TRANSFER;

        // Soft Penalty: Airline Clustering (Connected Components)
        Map<String, List<Integer>> airlineGatesMap = new HashMap<>();

        for (int i = 0; i < chromosome.length; i++) {
            int gateId = chromosome[i];
            Flight f = flights.get(i);
            
            // Extract the 2-letter airline prefix (e.g., "UA" from "UA-123")
            // Make sure the split array holds at least 2 elements so it doesn't crash on bad data
            String[] parts = f.getFlightCode().split("-");
            String airlineCode = parts.length > 1 ? parts[0] : f.getFlightCode().substring(0, Math.min(2, f.getFlightCode().length()));
            
            airlineGatesMap.computeIfAbsent(airlineCode, k -> new ArrayList<>()).add(gateId);
        }

        for (Map.Entry<String, List<Integer>> entry : airlineGatesMap.entrySet()) {
            List<Integer> gates = entry.getValue();
            if (gates.size() > 1) {
                int components = countConnectedComponents(gates, graph);
                if (components > 1) {
                    // Apply a penalty of 1500 for every distinct, disconnected cluster beyond the first
                    softPenalties += ((components - 1) * 15000.0);
                }
            }
        }

        double score = reward - hardPenalties - softPenalties;
        return score;
    }
}
