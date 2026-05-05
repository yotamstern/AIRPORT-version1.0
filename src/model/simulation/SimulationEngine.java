package model.simulation;

import model.Flight;
import model.FlightRepository;
import model.Gate;
import model.algo.genetic.GeneticEngine;
import model.enums.GateSize;
import model.enums.PlaneType;
import model.spatial.TerminalGraph;
import model.state.*;

import javax.swing.SwingWorker;
import java.util.*;

/**
 * Drives the live airport simulation once the Genetic Algorithm has produced an
 * initial schedule.
 *
 * <p>The engine is called once per simulated minute (a "tick") by the
 * {@link SimulationClock}. Each tick advances every flight through its FSM lifecycle:
 * Planned → Approaching → Landed → AtGate → Departed (or HoldingState if no gate
 * is available when the plane lands).
 *
 * <p>When the user injects a delay mid-simulation, the engine launches a lightweight
 * "Micro-GA" on a background thread to repair the schedule. The Micro-GA is much
 * smaller than the original GA (fewer generations, smaller population) because it
 * starts from the current near-optimal schedule rather than from scratch, so it
 * converges quickly without blocking the UI.
 */
public class SimulationEngine {

    private static final int TURNAROUND_BUFFER_MINUTES = 15;
    private static final int MICRO_GA_POPULATION = 60;
    private static final double MICRO_GA_MUTATION_RATE = 0.15;
    private static final int MICRO_GA_GENERATIONS = 50;

    private final List<Flight> flights;
    private final List<Gate> gates;
    private final TerminalGraph graph;

    /** The current best chromosome (index = flight position, value = gate ID). */
    private int[] currentChromosome;

    /** Called on the EDT after a repair completes so the UI can refresh. */
    private Runnable onRepairComplete;

    public SimulationEngine(List<Flight> flights, List<Gate> gates,
                            TerminalGraph graph, int[] initialChromosome) {
        this.flights = new ArrayList<>(flights);
        this.gates = gates;
        this.graph = graph;
        this.currentChromosome = initialChromosome.clone();
    }

    public void setOnRepairComplete(Runnable callback) {
        this.onRepairComplete = callback;
    }

    // -------------------------------------------------------------------------
    // Clock tick — called every simulated minute on the EDT
    // -------------------------------------------------------------------------

    /**
     * Advances all active flights by one simulated minute.
     * Departed flights are skipped because they're in a terminal state with no transitions.
     * After updating states, we check whether any holding flights can now be given a gate.
     */
    public void tick(int currentTime) {
        for (Flight f : flights) {
            if (!(f.getState() instanceof DepartedState)) {
                f.update(currentTime);
            }
        }
        tryAssignHoldingFlights();
    }

    /**
     * Scans holding flights every tick and assigns any that now have a free compatible gate.
     * This handles two scenarios: a gate that was freed by a departing plane, and flights
     * that were left unassigned by the GA's greedy sweep (unresolvable conflicts).
     * Holding flights are sorted by urgency so the most critical ones get priority.
     */
    private void tryAssignHoldingFlights() {
        List<Flight> holding = getHoldingFlights();
        if (holding.isEmpty()) return;

        // Build occupancy from all scheduled (non-holding, non-departed) flights
        Map<Integer, List<int[]>> occupancy = new HashMap<>();
        for (Gate g : gates) occupancy.put(g.getId(), new ArrayList<>());
        for (Flight f : flights) {
            if (f.getAssignedGate() != null && !(f.getState() instanceof DepartedState)
                    && !(f.getState() instanceof HoldingState)) {
                occupancy.get(f.getAssignedGate().getId())
                         .add(new int[]{f.getArrivalTime(), f.getDepartureTime()});
            }
        }

        // Sort holding flights by urgency (highest first) before assigning
        holding.sort((a, b) -> Double.compare(b.getUrgencyScore(), a.getUrgencyScore()));

        for (Flight f : holding) {
            boolean assigned = false;
            int i = 0;
            while (!assigned && i < gates.size()) {
                Gate g = gates.get(i);
                boolean isInternationalMatch = g.isInternational() == f.isInternational();
                boolean isLargeEnough = isGateLargeEnough(g.getSize(), f.getType());
                boolean hasFreeSlot = isFreeSlot(occupancy.get(g.getId()), f);

                if (isInternationalMatch && isLargeEnough && hasFreeSlot) {
                    f.setAssignedGate(g);
                    occupancy.get(g.getId()).add(new int[]{f.getArrivalTime(), f.getDepartureTime()});
                    assigned = true;
                } else {
                    i++;
                }
            }
        }
    }

    private boolean isGateLargeEnough(GateSize size, PlaneType type) {
        if (type == PlaneType.SMALL_BODY) return true;
        if (type == PlaneType.LARGE_BODY) return size == GateSize.SIZE_LARGE || size == GateSize.SIZE_JUMBO;
        return size == GateSize.SIZE_JUMBO; // JUMBO_BODY
    }

    private boolean isFreeSlot(List<int[]> occupancy, Flight f) {
        for (int[] interval : occupancy) {
            if (!(f.getArrivalTime() >= interval[1] + TURNAROUND_BUFFER_MINUTES
                    || interval[0] >= f.getDepartureTime() + TURNAROUND_BUFFER_MINUTES)) {
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Delay injection
    // -------------------------------------------------------------------------

    /**
     * Applies a delay to the target flight and asynchronously repairs the schedule.
     *
     * <p>Shifting a flight's arrival time can invalidate its current gate assignment
     * (the gate might now be occupied by another flight) and may cascade into
     * other conflicts. Rather than freezing the UI while we fix this, we push the
     * repair work onto a background thread and let the simulation clock keep ticking.
     * The UI refreshes once the repair completes via the {@code onRepairComplete} callback.
     */
    public void applyDelay(Flight target, int delayMinutes) {
        target.setArrivalTime(target.getArrivalTime() + delayMinutes);

        // Reset the delayed flight back to Planned so the FSM re-enters normally
        target.setState(new PlannedState());

        // Repair asynchronously so the simulation clock keeps ticking
        SwingWorker<int[], Void> repairWorker = new SwingWorker<>() {
            @Override
            protected int[] doInBackground() {
                return repairSchedule();
            }

            @Override
            protected void done() {
                try {
                    int[] repairedChromosome = get();
                    applyChromosomeToFlights(repairedChromosome);
                    currentChromosome = repairedChromosome;
                    if (onRepairComplete != null) {
                        onRepairComplete.run();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        repairWorker.execute();
    }

    // -------------------------------------------------------------------------
    // Warm-Start Micro-GA repair
    // -------------------------------------------------------------------------

    /**
     * Runs a small GA on a snapshot of the current schedule to resolve conflicts
     * introduced by a delay. Only unlocked flights (those not yet landed or at gate)
     * are eligible for re-assignment — committed flights stay exactly where they are.
     *
     * <p>We use a much smaller population and fewer generations than the initial GA
     * because the current chromosome is already close to optimal. The Micro-GA just
     * needs to shuffle a handful of conflicting flights, not solve the whole problem.
     */
    private int[] repairSchedule() {
        // Flights that have already landed or are at the gate have physical commitments
        // that can't be undone — lock them so the GA doesn't touch their assignments
        Set<Integer> lockedIndices = new HashSet<>();
        for (int i = 0; i < flights.size(); i++) {
            FlightState state = flights.get(i).getState();
            if (state instanceof AtGateState
                    || state instanceof LandedState
                    || state instanceof DepartedState) {
                lockedIndices.add(i);
            }
        }

        // Rebuild the repository from the live flight list so the GA sees the updated
        // arrival times (the delay may have changed them)
        FlightRepository liveRepo = new FlightRepository();
        for (Flight f : flights) {
            liveRepo.addFlight(f);
        }

        GeneticEngine repairEngine = new GeneticEngine(liveRepo, gates, graph);
        repairEngine.setParameters(MICRO_GA_POPULATION, MICRO_GA_MUTATION_RATE, MICRO_GA_GENERATIONS);

        System.out.println("[Micro-GA] Starting repair with " + lockedIndices.size()
                + " locked flights out of " + flights.size());

        return repairEngine.runRepair(currentChromosome, lockedIndices);
    }

    /**
     * Pushes the chromosome's gate assignments back onto the mutable Flight objects.
     * Locked (already-departed/at-gate) flights are left untouched.
     */
    private void applyChromosomeToFlights(int[] chromosome) {
        Map<Integer, Gate> gateById = new HashMap<>();
        for (Gate g : gates) {
            gateById.put(g.getId(), g);
        }

        for (int i = 0; i < flights.size(); i++) {
            Flight f = flights.get(i);
            FlightState state = f.getState();
            // Only update mutable flights
            if (!(state instanceof AtGateState)
                    && !(state instanceof LandedState)
                    && !(state instanceof DepartedState)) {
                int gateId = chromosome[i];
                f.setAssignedGate(gateById.get(gateId));
            }
        }

        // Guarantee zero collisions: evict any remaining overlaps to holding
        resolveConflictsToHolding();
    }

    /**
     * Public entry point for validating the initial schedule before simulation
     * starts. Ensures the GA output has no residual overlaps.
     */
    public void validateInitialSchedule() {
        resolveConflictsToHolding();
    }

    /**
     * Deterministic collision eliminator. Scans every gate's mutable flights,
     * finds overlapping pairs, and sends the lower-urgency flight to holding
     * by nulling its assignedGate. Locked flights are never touched.
     * O(N log N) — safe to call on every repair.
     */
    private void resolveConflictsToHolding() {
        Map<Integer, List<Flight>> byGate = new HashMap<>();
        for (Flight f : flights) {
            if (f.getAssignedGate() != null && !(f.getState() instanceof AtGateState)
                    && !(f.getState() instanceof LandedState)
                    && !(f.getState() instanceof DepartedState)) {
                byGate.computeIfAbsent(f.getAssignedGate().getId(), k -> new ArrayList<>()).add(f);
            }
        }

        for (List<Flight> gateFlights : byGate.values()) {
            gateFlights.sort(Comparator.comparingInt(Flight::getArrivalTime));
            Flight lastValid = gateFlights.get(0);
            for (int k = 1; k < gateFlights.size(); k++) {
                lastValid = processOverlap(lastValid, gateFlights.get(k));
            }
        }
    }

    private Flight processOverlap(Flight lastValid, Flight curr) {
        if (curr.getArrivalTime() >= lastValid.getDepartureTime()) {
            return curr;
        }

        // Real overlap — evict the lower-urgency flight to holding
        if (curr.getUrgencyScore() >= lastValid.getUrgencyScore()) {
            System.out.println("[Validator] Conflict: " + lastValid.getFlightCode()
                    + " evicted to holding (overlap with " + curr.getFlightCode() + ")");
            lastValid.setAssignedGate(null);
            return curr;
        }

        System.out.println("[Validator] Conflict: " + curr.getFlightCode()
                + " evicted to holding (overlap with " + lastValid.getFlightCode() + ")");
        curr.setAssignedGate(null);
        return lastValid;
    }

    // -------------------------------------------------------------------------
    // Accessors for the UI
    // -------------------------------------------------------------------------

    public List<Flight> getFlights() {
        return Collections.unmodifiableList(flights);
    }

    public int[] getCurrentChromosome() {
        return currentChromosome.clone();
    }

    /** Count of flights currently in HoldingState or waiting for a gate assignment. */
    public long getHoldingCount() {
        return getHoldingFlights().size();
    }

    /** Count of flights currently AtGate. */
    public long getAtGateCount() {
        return flights.stream().filter(f -> f.getState() instanceof AtGateState).count();
    }

    /** Count of departed flights. */
    public long getDepartedCount() {
        return flights.stream().filter(f -> f.getState() instanceof DepartedState).count();
    }

    /** Returns flights currently waiting for a gate assignment. */
    public List<Flight> getHoldingFlights() {
        List<Flight> holding = new ArrayList<>();
        for (Flight f : flights) {
            if (f.getState() instanceof HoldingState) {
                holding.add(f);
            } else if ((f.getState() instanceof PlannedState || f.getState() instanceof ApproachingState) 
                       && f.getAssignedGate() == null) {
                holding.add(f);
            }
        }
        return holding;
    }

    /** Returns only flights that have not yet departed (relevant for the delay dialog). */
    public List<Flight> getActiveFligths() {
        List<Flight> active = new ArrayList<>();
        for (Flight f : flights) {
            if (!(f.getState() instanceof DepartedState)) {
                active.add(f);
            }
        }
        return active;
    }

    /** Returns Planned + Approaching flights — both can still be meaningfully delayed. */
    public List<Flight> getDelayableFlights() {
        List<Flight> result = new ArrayList<>();
        for (Flight f : flights) {
            if (f.getState() instanceof PlannedState
                    || f.getState() instanceof ApproachingState) {
                result.add(f);
            }
        }
        return result;
    }

    /** Returns only flights still in PlannedState — the only ones that can be meaningfully delayed. */
    public List<Flight> getPlannedFlights() {
        List<Flight> planned = new ArrayList<>();
        for (Flight f : flights) {
            if (f.getState() instanceof PlannedState) {
                planned.add(f);
            }
        }
        return planned;
    }
}
