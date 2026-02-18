package test;

import model.Flight;
import model.FlightRepository;
import model.Gate;
import model.algo.GreedyInitializer;
import model.algo.genetic.GeneticEngine;
import model.enums.GateSize;
import model.enums.PlaneType;
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

            // Create Mock Flights - SOLVABLE SCENARIO
            // Batch 1: Early Morning (08:00 - 08:20) -> Departs by 09:05 max
            repo.addFlight(new Flight(1, "F01", 480, PlaneType.SMALL_BODY, 50.0)); // 08:00
            repo.addFlight(new Flight(2, "F02", 485, PlaneType.LARGE_BODY, 30.0)); // 08:05
            repo.addFlight(new Flight(3, "F03", 490, PlaneType.JUMBO_BODY, 10.0)); // 08:10
            repo.addFlight(new Flight(4, "F04", 495, PlaneType.SMALL_BODY, 45.0)); // 08:15
            repo.addFlight(new Flight(5, "F05", 500, PlaneType.JUMBO_BODY, 15.0)); // 08:20

            // Batch 2: Mid Morning (09:30 - 09:50) -> Safety buffer of ~25 mins from Batch
            // 1
            repo.addFlight(new Flight(6, "F06", 570, PlaneType.LARGE_BODY, 25.0)); // 09:30
            repo.addFlight(new Flight(7, "F07", 575, PlaneType.SMALL_BODY, 20.0)); // 09:35
            repo.addFlight(new Flight(8, "F08", 580, PlaneType.LARGE_BODY, 18.0)); // 09:40
            repo.addFlight(new Flight(9, "F09", 585, PlaneType.JUMBO_BODY, 12.0)); // 09:45
            repo.addFlight(new Flight(10, "F10", 590, PlaneType.SMALL_BODY, 5.0)); // 09:50

            // Explicitly set Turnaround Time
            System.out.println("\n[DEBUG] Flight Times (Arr -> Dep):");
            for (Flight f : repo.getAllFlights()) {
                f.setServiceDuration(45);
                String times = String.format("%02d:%02d -> %02d:%02d",
                        f.getArrivalTime() / 60, f.getArrivalTime() % 60,
                        f.getDepartureTime() / 60, f.getDepartureTime() % 60);
                System.out.println("Flight " + f.getFlightCode() + ": " + times);
            }

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
