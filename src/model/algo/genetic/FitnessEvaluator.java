package model.algo.genetic;

import model.Flight;
import model.FlightRepository;
import model.spatial.TerminalGraph;
import java.util.List;

/**
 * Component responsible for calculating the fitness score of a schedule.
 * Fitness guides the evolution process.
 */
public class FitnessEvaluator {
    private TerminalGraph graph;
    private FlightRepository repo; // Not strictly needed if we pass list, but good for lookups if needed.

    // Constants
    private static final double BASE_SCORE = 10000.0;
    private static final double HARD_PENALTY = 1000.0; // Per collision
    private static final double SOFT_PENALTY = 0.1; // Per unit of walking distance

    public FitnessEvaluator(TerminalGraph graph, FlightRepository repo) {
        this.graph = graph;
        this.repo = repo;
    }

    /**
     * Calculates the fitness of a chromosome (schedule).
     * <p>
     * <b>Time Complexity:</b> O(N^2) due to collision checking.
     * Walking time calculation is O(1) per flight (if connecting info exists) or
     * O(N) if we simulate all pairs.
     * Here we just simulate a dummy valid check.
     * </p>
     * 
     * @param chromosome The schedule to evaluate.
     * @param flights    The list of flights.
     * @return The fitness score.
     */
    public double calculateFitness(int[] chromosome, List<Flight> flights) {
        int collisions = ConstraintChecker.countCollisions(chromosome, flights);

        // Calculate Total Walking Time (Soft Constraint)
        // For simplicity in this phase, let's assume valid connections between specific
        // flights?
        // OR just minimize distance from a central point?
        // Requirement: "Calculate walking time for all connecting passengers using
        // graph.getPathWeight()"
        // Since we don't have explicit "Connecting Passengers" data in Flight class
        // yet,
        // let's simulate it: Minimize distance from Gate X to a "Hub" (Gate 0/1) for
        // all flights?
        // Or assume sequential flights 1->2, 3->4 connect?
        // Let's assume sequential flights might have transfer passengers for the sake
        // of the formula.
        // Or better: Distance from Entrance (Gate 1).

        double totalWalkingDistance = 0;

        // Dummy logic: Sum of distances from Gate 1 to Assigned Gate (simulating entry
        // to gate walk)
        for (int gateId : chromosome) {
            // If gateId is 1, distance is 0.
            // We need to handle if path doesn't exist?
            double dist = graph.getPathWeight(1, gateId);
            if (dist != -1) {
                totalWalkingDistance += dist;
            } else {
                totalWalkingDistance += 1000; // Penalty for unreachable???
            }
        }

        double score = BASE_SCORE
                - (collisions * HARD_PENALTY)
                - (totalWalkingDistance * SOFT_PENALTY);

        return Math.max(0, score); // Ensure non-negative
    }
}
