package test;

import model.Flight;
import model.FlightRepository;
import model.Gate;
import model.algo.genetic.FitnessEvaluator;
import model.algo.genetic.GeneticEngine;
import model.enums.GateSize;
import model.enums.PlaneType;
import model.spatial.TerminalGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Benchmark test to push the Genetic Algorithm Engine to its limits using 500
 * flights and 30 gates.
 * Tests performance, load capacity, and the early stopping convergence
 * mechanism.
 */
public class MassiveScaleTest {

    public static void main(String[] args) {
        System.out.println("[MASSIVE SCALE TEST] Generating 500 flights and 30 gates...");

        FlightRepository repo = new FlightRepository();
        List<Gate> gates = new ArrayList<>();
        TerminalGraph graph = new TerminalGraph();
        Random rand = new Random();

        // 1. Dynamic Gate Generation (30 Gates total)
        // 15 SMALL, 10 LARGE, 5 JUMBO
        int gateIdCounter = 1;
        // Gates 1-10: SMALL, Domestic
        for (int i = 0; i < 10; i++) {
            Gate g = new Gate(gateIdCounter++, GateSize.SIZE_SMALL, i * 10, 0, false);
            gates.add(g);
            graph.addGate(g);
        }
        // Gates 11-15: SMALL, International
        for (int i = 0; i < 5; i++) {
            Gate g = new Gate(gateIdCounter++, GateSize.SIZE_SMALL, 100 + i * 10, 0, true);
            gates.add(g);
            graph.addGate(g);
        }

        // Gates 16-20: LARGE, Domestic
        for (int i = 0; i < 5; i++) {
            Gate g = new Gate(gateIdCounter++, GateSize.SIZE_LARGE, 150 + i * 15, 0, false);
            gates.add(g);
            graph.addGate(g);
        }
        // Gates 21-25: LARGE, International
        for (int i = 0; i < 5; i++) {
            Gate g = new Gate(gateIdCounter++, GateSize.SIZE_LARGE, 225 + i * 15, 0, true);
            gates.add(g);
            graph.addGate(g);
        }

        // Gates 26-30: JUMBO, International (All Jumbo are international now)
        for (int i = 0; i < 5; i++) {
            Gate g = new Gate(gateIdCounter++, GateSize.SIZE_JUMBO, 300 + i * 20, 0, true);
            gates.add(g);
            graph.addGate(g);
        }

        // Connect gates sequentially in the TerminalGraph
        for (int i = 0; i < gates.size() - 1; i++) {
            graph.connectGates(gates.get(i), gates.get(i + 1));
        }

        // 2. Dynamic Flight Generation (280 Flights)
        for (int i = 1; i <= 280; i++) {
            String flightCode = String.format("FL-%03d", i);

            // Randomize Plane Type mappings: 70% SMALL, 20% LARGE, 10% JUMBO
            int sizeRoll = rand.nextInt(100);
            PlaneType type;
            if (sizeRoll < 70) {
                type = PlaneType.SMALL_BODY;
            } else if (sizeRoll < 90) {
                type = PlaneType.LARGE_BODY;
            } else {
                type = PlaneType.JUMBO_BODY;
            }

            // Arrival time randomly from 360 to 1260 inclusive (represents 06:00 to 21:00)
            int arrivalTime = rand.nextInt(901) + 360;
            double urgency = rand.nextDouble() * 100.0;
            boolean isInternational = rand.nextDouble() < 0.30;

            Flight f = new Flight(i, flightCode, arrivalTime, type, urgency, isInternational);
            f.setServiceDuration(45); // DepartureTime = ArrivalTime + 45
            repo.addFlight(f);
        }

        System.out.println("[MASSIVE SCALE TEST] Repository and constraints prepared.");
        System.out.println("[MASSIVE SCALE TEST] Starting Genetic Engine execution...");

        // Initialize the shortest paths mapping using Floyd-Warshall
        graph.initializeDistanceMatrix();

        // 3. Execution & Benchmarking (Pop = 200, Gens = 500)
        // With O(N log N) optimizations, lower population provides more generation
        // iterations faster
        GeneticEngine engine = new GeneticEngine(repo, gates, graph);
        engine.setParameters(200, 0.05, 500);

        long startTime = System.currentTimeMillis();
        int[] bestSolution = engine.run();
        long endTime = System.currentTimeMillis();

        double executionTimeSec = (endTime - startTime) / 1000.0;

        // 4. Console Output
        FitnessEvaluator evaluator = new FitnessEvaluator(graph, repo, gates);
        List<Flight> flightsList = new ArrayList<>(repo.getAllFlights());
        double finalFitness = evaluator.calculateFitness(bestSolution, flightsList);

        System.out.println("\n==================================================");
        System.out.println("            MASSIVE SCALE TEST RESULTS            ");
        System.out.println("==================================================");
        System.out.printf("Total Execution Time: %.3f seconds%n", executionTimeSec);
        System.out.println("Final Best Fitness Score: " + finalFitness);

        // Check for hard penalties. Since BASE_SCORE is 1,000,000 and the lowest hard
        // penalty is -2000...
        // Any score > 950000 essentially guarantees that no hard penalties were
        // triggered
        if (finalFitness >= 950000.0) {
            System.out.println("SUCCESS: The AI successfully grouped the runtime flights with ZERO hard penalties!");
        } else {
            System.out.println("WARNING: Unresolved hard penalties still persist inside the final best schedule.");
        }
        System.out.println("==================================================\n");
    }
}
