package model.algo;

import model.Flight;
import model.Gate;
import model.FlightRepository;
import model.ds.FlightMinHeap;
import model.enums.GateSize;
import model.enums.PlaneType;

import java.util.*;

/**
 * Produces a greedy initial gate assignment by processing flights in urgency order.
 * Used to seed part of the GA's starting population with a reasonable solution
 * rather than relying entirely on random chromosomes.
 */
public class GreedyInitializer {
    private FlightRepository flightRepo;
    private List<Gate> gates;

    public GreedyInitializer(FlightRepository flightRepo, List<Gate> gates) {
        this.flightRepo = flightRepo;
        this.gates = gates;
    }

    /**
     * Assigns flights to gates in urgency order. Preference: exact size match first,
     * then oversized, then force-assign and let the GA clean up any overlaps.
     *
     * Time complexity: O(N log N + N*G) — heap extraction is O(log N), gate scan is O(G).
     *
     * @return Map of Flight ID → Gate ID.
     */
    public Map<Integer, Integer> generateInitialSolution() {
        Map<Integer, Integer> schedule = new HashMap<>();
        FlightMinHeap heap = new FlightMinHeap(flightRepo.getAllFlights().size() + 10);
        
        // Map to track when each gate will become available again
        Map<Integer, Integer> gateFreeAt = new HashMap<>();
        for (Gate g : gates) {
            gateFreeAt.put(g.getId(), 0);
        }

        // Step 1: Load all flights into heap - O(N log N)
        for (Flight f : flightRepo.getAllFlights()) {
            heap.insert(f);
        }

        // Step 2: Process heap
        while (!heap.isEmpty()) {
            Flight f = heap.extractMin();
            
            // First Priority: Try to find an exact size match to prevent wasting Jumbo gates on Small planes!
            boolean assigned = tryAssignGate(f, schedule, gateFreeAt, true, true);
            
            // Second Priority: Fall back to allowing spillover into larger gates if the exact ones are busy
            if (!assigned) {
                assigned = tryAssignGate(f, schedule, gateFreeAt, false, true);
            }
            
            // Third Priority: If it couldn't be scheduled cleanly at all, force it into an exact matching gate (letting the GA fix the overlap)
            if (!assigned) {
                tryAssignGate(f, schedule, gateFreeAt, true, false);
            }
        }

        return schedule;
    }

    private boolean tryAssignGate(Flight f, Map<Integer, Integer> schedule, Map<Integer, Integer> gateFreeAt, boolean requireExact, boolean requireFreeTime) {
        Iterator<Gate> gateIter = gates.iterator();
        while (gateIter.hasNext()) {
            Gate g = gateIter.next();
            boolean canPark = requireExact ? canParkExact(g, f) : canPark(g, f);
            
            if (canPark) {
                if (!requireFreeTime || gateFreeAt.get(g.getId()) <= f.getArrivalTime()) {
                    schedule.put(f.getId(), g.getId());
                    if (requireFreeTime) {
                        gateFreeAt.put(g.getId(), f.getDepartureTime() + 15);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canPark(Gate g, Flight f) {
        if (f.isInternational() != g.isInternational()) return false;
        
        PlaneType type = f.getType();
        GateSize size = g.getSize();
        
        if (size == GateSize.SIZE_JUMBO) return true;
        if (size == GateSize.SIZE_LARGE) return type != PlaneType.JUMBO_BODY;
        return type == PlaneType.SMALL_BODY;
    }

    private boolean canParkExact(Gate g, Flight f) {
        if (f.isInternational() != g.isInternational()) return false;
        
        PlaneType type = f.getType();
        GateSize size = g.getSize();
        
        if (size == GateSize.SIZE_JUMBO && type == PlaneType.JUMBO_BODY) return true;
        if (size == GateSize.SIZE_LARGE && type == PlaneType.LARGE_BODY) return true;
        return size == GateSize.SIZE_SMALL && type == PlaneType.SMALL_BODY;
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
