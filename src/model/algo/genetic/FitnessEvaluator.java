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
 * Scores a gate-assignment schedule (chromosome) so the GA can tell which of two
 * schedules is better. Think of it like a report card: each correctly assigned flight
 * earns reward points, while violations subtract penalty points.
 *
 * <p><b>Hard penalties</b> (very large) punish things that are simply illegal —
 * two planes sharing a gate at the same time, a jumbo jet squeezed into a small gate,
 * or a domestic flight sent to an international terminal. Their huge size ensures
 * the GA will almost always prefer fixing a hard violation over optimizing anything soft.
 *
 * <p><b>Soft penalties</b> (small) nudge the GA toward convenience goals that matter
 * for passenger experience: shorter walks from the entrance, minimal wasted gate space,
 * short connecting-passenger walks between gates, and keeping all flights of the same
 * airline clustered together.
 *
 * <p>Final score: {@code reward − hardPenalties − softPenalties}.
 * Higher is better; a perfect schedule with no violations has the highest possible score.
 */
public class FitnessEvaluator {
    private TerminalGraph graph;
    private FlightRepository repo;
    private Map<Integer, Gate> gateMap;

    // --- Reward ---
    private static final double REWARD_VALID_FLIGHT = 10000.0; // awarded per correctly assigned flight

    // --- Hard penalties (must dwarf the reward so the GA never sacrifices correctness for soft gains) ---
    private static final double HARD_PENALTY_OVERLAP = 15000.0; // two planes at the same gate simultaneously
    private static final double HARD_PENALTY_SIZE = 50000.0;    // gate too small for the plane type
    private static final double HARD_PENALTY_INT = 50000.0;     // domestic plane at international terminal (or vice-versa)

    // --- Soft penalties (guide quality improvements once hard violations are resolved) ---
    private static final double SOFT_PENALTY_WALK = 0.1;        // per passenger·distance-unit from the entrance
    private static final double SOFT_PENALTY_BUFFER = 50.0;     // per gate with less than BUFFER_TIME_THRESHOLD between consecutive flights
    private static final double SOFT_PENALTY_TRANSFER = 0.1;    // per connecting passenger·distance-unit between their two gates
    private static final double SOFT_PENALTY_WASTE = 2000.0;    // per wasted-space level (e.g., small plane at a jumbo gate)

    private static final int ENTRANCE_GATE_ID = 1;              // walking distance is measured from gate 1 (the terminal entrance)
    private static final double UNREACHABLE_GATE_PENALTY = 1000.0; // large flat penalty when a gate has no graph path from the entrance
    private static final double AIRLINE_CLUSTERING_PENALTY = 1500.0; // per extra disconnected cluster of gates for the same airline
    private static final int AIRLINE_CODE_LENGTH = 2;           // "UA" from "UA-123" — the prefix before the dash
    private static final int BUFFER_TIME_THRESHOLD = 15;        // minimum minutes required between consecutive flights at a gate

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
     * Measures how much bigger the gate is than the plane actually needs.
     * Returns 0 for a perfect fit, 1 for one size too large, 2 for two sizes too large.
     * A small plane at a jumbo gate isn't illegal, but it wastes expensive infrastructure
     * — so the GA is nudged to prefer exact-fit assignments via a soft penalty.
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
     * Counts how many separate clusters exist among gates used by the same airline.
     * Fewer clusters = better grouping = lower soft penalty.
     * BFS over the terminal graph — gates that are physically adjacent form one cluster.
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
     * Returns true if the gate can physically accommodate the plane.
     * Small planes fit anywhere; large planes need a large or jumbo gate;
     * jumbo planes can only use jumbo gates.
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
     * Scores a complete gate-assignment schedule.
     *
     * <p>The chromosome is an int array where {@code chromosome[i]} is the gate ID
     * assigned to {@code flights.get(i)}. The method walks through every flight,
     * every gate's time-sorted queue, every passenger walk, every connecting transfer,
     * and every airline cluster — accumulating rewards and penalties as it goes.
     *
     * @param chromosome the schedule to score (index → gate ID mapping)
     * @param flights    flight list ordered the same way as the chromosome
     * @return fitness score; higher is better, negative means many hard violations
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

            Gate gate = gateMap.get(gateId);
            boolean isValidSize = gate != null && isGateLargeEnough(gate.getSize(), f.getType());
            boolean isValidInt = gate != null && f.isInternational() == gate.isInternational();

            if (!isValidSize) {
                hardPenalties += HARD_PENALTY_SIZE; // plane physically can't fit at this gate
            }

            if (!isValidInt) {
                hardPenalties += HARD_PENALTY_INT;  // domestic/international terminal mismatch
            }

            if (isValidSize && isValidInt) {
                reward += REWARD_VALID_FLIGHT;
                // Penalise over-sized assignments (e.g., small plane at a jumbo gate)
                softPenalties += getWastedSpaceLevel(gate.getSize(), f.getType()) * SOFT_PENALTY_WASTE;
            }
        }

        // Check each gate's time-sorted queue for overlapping flights and tight buffers.
        // Sorting by arrival time lets us compare only adjacent pairs (O(N log N) total).
        for (Map.Entry<Integer, List<Flight>> entry : gateAssignments.entrySet()) {
            List<Flight> assignedFlights = entry.getValue();
            assignedFlights.sort(Comparator.comparingInt(Flight::getArrivalTime));

            for (int i = 0; i < assignedFlights.size() - 1; i++) {
                Flight f1 = assignedFlights.get(i);
                Flight fNext = assignedFlights.get(i + 1);

                if (fNext.getArrivalTime() < f1.getDepartureTime()) {
                    // Two planes occupying the same gate at the same time — hard violation
                    hardPenalties += HARD_PENALTY_OVERLAP;
                } else {
                    // No overlap, but check whether ground crews have enough turnaround time
                    int buffer = fNext.getArrivalTime() - f1.getDepartureTime();
                    if (buffer < BUFFER_TIME_THRESHOLD) {
                        softPenalties += SOFT_PENALTY_BUFFER;
                    }
                }
            }
        }

        // Walking distance: penalise long walks from the terminal entrance to each gate.
        // Weighted by passenger count — a full jumbo jet walking far costs much more
        // than a near-empty regional flight at the same distance.
        double totalWalkingDistance = 0;
        for (int i = 0; i < chromosome.length; i++) {
            int gateId = chromosome[i];
            Flight f = flights.get(i);
            int pax = Math.max(1, f.getPassengerCount()); // treat 0 as 1 so penalty is never nullified
            double dist = graph.getShortestDistance(ENTRANCE_GATE_ID, gateId);
            if (dist != Double.POSITIVE_INFINITY) {
                totalWalkingDistance += dist * pax;
            } else {
                // Gate has no path from the entrance — apply a large flat penalty
                totalWalkingDistance += UNREACHABLE_GATE_PENALTY * pax;
            }
        }
        softPenalties += totalWalkingDistance * SOFT_PENALTY_WALK;

        // Transfer distance: passengers connecting between two flights have to walk
        // from the arrival gate to the departure gate. Minimising this benefits
        // tight-connection passengers the most.
        double totalTransferDistance = 0;
        if (repo.getAllTransfers() != null) {
            for (Transfer transfer : repo.getAllTransfers()) {
                Integer fromGateId = flightIdToGateMap.get(transfer.getFromFlightId());
                Integer toGateId = flightIdToGateMap.get(transfer.getToFlightId());

                if (fromGateId != null && toGateId != null) {
                    double dist = graph.getShortestDistance(fromGateId, toGateId);
                    if (dist != Double.POSITIVE_INFINITY) {
                        totalTransferDistance += dist * transfer.getNumPassengers();
                    } else {
                        totalTransferDistance += UNREACHABLE_GATE_PENALTY * transfer.getNumPassengers();
                    }
                }
            }
        }
        softPenalties += totalTransferDistance * SOFT_PENALTY_TRANSFER;

        // Airline clustering: ideally all flights of the same airline use adjacent gates
        // so their ground crews and check-in desks are co-located. We count how many
        // disconnected gate clusters each airline has and penalise every extra cluster
        // beyond the first.
        Map<String, List<Integer>> airlineGatesMap = new HashMap<>();
        for (int i = 0; i < chromosome.length; i++) {
            int gateId = chromosome[i];
            Flight f = flights.get(i);

            // Extract airline prefix from flight code (e.g., "UA" from "UA-123").
            // Fall back to the first AIRLINE_CODE_LENGTH characters if there is no dash.
            String[] parts = f.getFlightCode().split("-");
            String airlineCode = parts.length > 1 ? parts[0]
                    : f.getFlightCode().substring(0, Math.min(AIRLINE_CODE_LENGTH, f.getFlightCode().length()));

            airlineGatesMap.computeIfAbsent(airlineCode, k -> new ArrayList<>()).add(gateId);
        }

        for (Map.Entry<String, List<Integer>> entry : airlineGatesMap.entrySet()) {
            List<Integer> gates = entry.getValue();
            if (gates.size() > 1) {
                int components = countConnectedComponents(gates, graph);
                if (components > 1) {
                    softPenalties += ((components - 1) * AIRLINE_CLUSTERING_PENALTY);
                }
            }
        }

        double score = reward - hardPenalties - softPenalties;
        return score;
    }
}
