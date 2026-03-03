package test;

import model.Flight;
import model.FlightRepository;
import model.Gate;
import model.algo.GreedyInitializer;
import model.algo.genetic.GeneticEngine;
import model.enums.GateSize;

import model.spatial.TerminalGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * System Integration Test to verify that all components (Model, Spatial,
 * Greedy, Genetic) work together correctly.
 * Refactored to use a solvable scenario.
 */
public class SystemIntegrationTest {

    public static void main(String[] args) {
        System.out.println("=== SYSTEM INTEGRATION TEST START ===");

        try {
            // ==========================================
            // 1. Setup Environment
            // ==========================================
            System.out.println("\n[SETUP] Initializing mock data...");
            FlightRepository repo = new FlightRepository();
            TerminalGraph graph = new TerminalGraph();
            List<Gate> gates = new ArrayList<>();

            // Create Mock Gates (5 Gates)
            Gate g1 = new Gate(1, GateSize.SIZE_SMALL, 0, 0);
            Gate g2 = new Gate(2, GateSize.SIZE_LARGE, 10, 0);
            Gate g3 = new Gate(3, GateSize.SIZE_JUMBO, 20, 0);
            Gate g4 = new Gate(4, GateSize.SIZE_LARGE, 30, 0);
            Gate g5 = new Gate(5, GateSize.SIZE_SMALL, 40, 0);

            gates.add(g1);
            gates.add(g2);
            gates.add(g3);
            gates.add(g4);
            gates.add(g5);
            for (Gate g : gates)
                graph.addGate(g);

            // Connect Gates
            graph.connectGates(g1, g2);
            graph.connectGates(g2, g3);
            graph.connectGates(g3, g4);
            graph.connectGates(g4, g5);

            // Load 50 flights from real CSV data
            System.out.println("\n[SETUP] Loading flights from CSV...");
            model.utils.DataLoader.loadFlights(repo, "flights.csv");

            // Explicitly set Turnaround Time for all loaded flights to 45 mins
            System.out.println("\n[DEBUG] Setting 45-min turnaround & calculating departure times:");
            for (Flight f : repo.getAllFlights()) {
                f.setServiceDuration(45); // This effectively ensures departureTime = arrivalTime + 45

                String times = String.format("%02d:%02d -> %02d:%02d",
                        f.getArrivalTime() / 60, f.getArrivalTime() % 60,
                        f.getDepartureTime() / 60, f.getDepartureTime() % 60);
                // Print a few for debugging instead of all 50
                if (f.getId() <= 5 || f.getId() >= 46) {
                    System.out.println("Flight " + f.getFlightCode() + ": " + times);
                }
            }
            System.out.println("... (listing truncated for brevity) ...");

            // ==========================================
            // 2. Verify Spatial Engine
            // ==========================================
            System.out.println("\n[TEST] Verifying Spatial Engine...");
            double dist = graph.getPathWeight(1, 5);
            System.out.println("[TEST] Distance Gate 1 -> Gate 5: " + dist);
            if (Math.abs(dist - 40.0) < 0.001)
                System.out.println("[PASS] Distance Correct.");
            else
                System.err.println("[FAIL] Distance Incorrect.");

            // ==========================================
            // 3. Verify Greedy Solver
            // ==========================================
            System.out.println("\n[TEST] Verifying Greedy Solver...");
            GreedyInitializer greedy = new GreedyInitializer(repo, gates);
            Map<Integer, Integer> initialSol = greedy.generateInitialSolution();
            System.out.println("[TEST] Greedy Assigned: " + initialSol.size() + "/" + repo.getAllFlights().size());

            // ==========================================
            // 4. Verify Genetic Engine
            // ==========================================
            System.out.println("\n[TEST] Verifying Genetic Engine...");
            GeneticEngine genetic = new GeneticEngine(repo, gates, graph);

            // Task 3: Load stress test scale (Pop=500, Rate=0.05, Gens=300)
            genetic.setParameters(500, 0.05, 300);

            int[] bestSol = genetic.run();

            // ==========================================
            // 5. Final Output
            // ==========================================
            System.out.println("\n[OUTPUT] Final Schedule:");
            List<Flight> flightList = new ArrayList<>(repo.getAllFlights());
            for (int i = 0; i < bestSol.length; i++) {
                int gateId = bestSol[i];
                Flight f = flightList.get(i);
                System.out.println(String.format("Flight %s -> Gate %d (Arr: %02d:%02d, Dep: %02d:%02d)",
                        f.getFlightCode(), gateId,
                        f.getArrivalTime() / 60, f.getArrivalTime() % 60,
                        f.getDepartureTime() / 60, f.getDepartureTime() % 60));
            }

            System.out.println("\n=== SYSTEM INTEGRATION TEST COMPLETE ===");

        } catch (Exception e) {
            System.err.println("[ERROR] Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
