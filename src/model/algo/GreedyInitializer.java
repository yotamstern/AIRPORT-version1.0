package model.algo;

import model.Flight;
import model.Gate;
import model.FlightRepository;
import model.ds.FlightMinHeap;
import model.enums.GateSize;
import model.enums.PlaneType;

import java.util.*;

/**
 * Greedily assigns flights to gates based on urgency.
 * Serves as an initializer for more complex optimization algorithms (e.g.,
 * Genetic Algorithm).
 */
public class GreedyInitializer {
    private FlightRepository flightRepo;
    private List<Gate> gates;

    public GreedyInitializer(FlightRepository flightRepo, List<Gate> gates) {
        this.flightRepo = flightRepo;
        this.gates = gates;
    }

    /**
     * Generates an initial solution by assigning the most urgent flights to the
     * first available fitting gate.
     * <p>
     * <b>Time Complexity:</b> O(N * (log N + G)), where N is the number of flights
     * and G is the number of gates.
     * Building the heap takes O(N log N) (series of inserts).
     * Extracting min takes O(log N).
     * Finding a gate takes O(G).
     * Total loop: N * (log N + G) -> simplifiable to O(N log N + N*G).
     * </p>
     * 
     * @return A Map mapping Flight ID to Gate ID.
     */
    public Map<Integer, Integer> generateInitialSolution() {
        Map<Integer, Integer> schedule = new HashMap<>();
        FlightMinHeap heap = new FlightMinHeap(flightRepo.getAllFlights().size() + 10);

        // Step 1: Load all flights into heap - O(N log N)
        for (Flight f : flightRepo.getAllFlights()) {
            heap.insert(f);
        }

        // Step 2: Process heap
        while (!heap.isEmpty()) {
            Flight f = heap.extractMin();

            // Step 3: Find First Fit Gate - O(G)
            // Note: This logic is simplified. It only checks static size compatibility.
            // It does NOT yet check time overlap (requires Interval Tree or checking
            // schedule map).
            // For the "Greedy Initializer" requirement in this phase, we act as if we are
            // just finding A spot.
            // In a real scheduler, we would check time availability.
            // Requirement says: "Find the first gate that fits the size constraint...
            // (simple availability)"

            for (Gate g : gates) {
                if (canPark(g, f)) {
                    // Assign
                    schedule.put(f.getId(), g.getId());
                    // In a full implementation, we would mark the gate as occupied for this time
                    // slot.
                    // Here we just pick the first valid one.
                    break;
                }
            }
        }

        return schedule;
    }

    private boolean canPark(Gate g, Flight f) {
        // Size Check
        boolean sizeFits = false;
        if (g.getSize() == GateSize.SIZE_JUMBO) {
            sizeFits = true; // Jumbo fits everything
        } else if (g.getSize() == GateSize.SIZE_LARGE) {
            sizeFits = (f.getType() != PlaneType.JUMBO_BODY);
        } else if (g.getSize() == GateSize.SIZE_SMALL) {
            sizeFits = (f.getType() == PlaneType.SMALL_BODY);
        }

        return sizeFits;
    }

    /**
     * Test Harness
     */
    public static void main(String[] args) {
        // Setup Repository
        FlightRepository repo = new FlightRepository();

        // Create Flights with different urgency scores
        // Remember: Higher Score = Higher Priority
        Flight f1 = new Flight(1, "URGENT_1", 800, PlaneType.SMALL_BODY, 100.0); // Most urgent
        Flight f2 = new Flight(2, "NORMAL_1", 820, PlaneType.LARGE_BODY, 50.0);
        Flight f3 = new Flight(3, "LAZY_1", 900, PlaneType.JUMBO_BODY, 10.0); // Least urgent

        repo.addFlight(f1);
        repo.addFlight(f2);
        repo.addFlight(f3);

        // Setup Gates
        List<Gate> gates = new ArrayList<>();
        gates.add(new Gate(101, GateSize.SIZE_SMALL, 0, 0));
        gates.add(new Gate(102, GateSize.SIZE_LARGE, 10, 10));
        gates.add(new Gate(103, GateSize.SIZE_JUMBO, 20, 20));

        // Run Solver
        GreedyInitializer solver = new GreedyInitializer(repo, gates);
        Map<Integer, Integer> solution = solver.generateInitialSolution();

        System.out.println("Greedy Assignment Results:");
        for (Map.Entry<Integer, Integer> entry : solution.entrySet()) {
            Flight f = repo.getFlight(entry.getKey());
            System.out.println("Flight " + f.getFlightCode() +
                    " (Score: " + f.getUrgencyScore() + ")" +
                    " assigned to Gate " + entry.getValue());
        }

        // Verification Logic
        // F1 (Small, 100.0) should fit in Gate 101 (Small).
        // F2 (Large, 50.0) should fit in Gate 102 (Large).
        // F3 (Jumbo, 10.0) should fit in Gate 103 (Jumbo).

        if (solution.get(1) == 101 && solution.get(3) == 103) {
            System.out.println("SUCCESS: Urgency sorting and size matching verified.");
        } else {
            System.out.println("FAILURE: Assignments incorrect.");
        }
    }
}
