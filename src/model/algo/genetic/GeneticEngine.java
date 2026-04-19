package model.algo.genetic;

import model.Flight;
import model.FlightRepository;
import model.Gate;
import model.algo.GreedyInitializer;
import model.ds.FlightMinHeap;
import model.enums.PlaneType;
import model.spatial.TerminalGraph;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Runs the genetic algorithm to find an optimal gate assignment schedule.
 * Population evolves over generations using tournament selection,
 * two-point crossover, and conflict-directed mutation.
 */
public class GeneticEngine {
    private FlightRepository flightRepo;
    private List<Gate> gates;
    private TerminalGraph graph;

    private List<Flight> flights;
    private List<int[]> population;

    // BN-3 fix: single shared Random instance instead of new Random() per call
    private final Random rand = new Random();

    // BN-5 fix: gate lookup built once in constructor
    private final Map<Integer, Gate> gateMap = new HashMap<>();

    // BN-4 fix: valid-gate lookup built once in constructor
    // Key: PlaneType -> isInternational -> list of valid gates (exact-size first)
    private final Map<PlaneType, Map<Boolean, List<Gate>>> validGatesCache = new EnumMap<>(PlaneType.class);

    // Concurrency Controls (Phase 11)
    private volatile boolean isPaused = false;

    // Repair mode: genes at these indices must not be mutated or crossed-over
    private Set<Integer> lockedIndices = Collections.emptySet();

    // GA Parameters (Instance variables for Parameter Tuning)
    private int populationSize = 100;
    private double mutationRate = 0.05;
    private int maxGenerations = 50;
    private int tournamentSize = 5;

    // Scale Optimization constants
    private static final int ELITISM_COUNT = 2;
    private static final int MAX_STAGNANT_GENERATIONS = 50;

    public GeneticEngine(FlightRepository flightRepo, List<Gate> gates, TerminalGraph graph) {
        this.flightRepo = flightRepo;
        this.gates = gates;
        this.graph = graph;
        this.flights = new ArrayList<>(flightRepo.getAllFlights());
        this.population = new ArrayList<>();

        // BN-5: build gateMap once
        for (Gate g : gates) {
            gateMap.put(g.getId(), g);
        }

        // BN-4: build validGatesCache once for every (PlaneType, isInternational) combo
        FitnessEvaluator tempEval = new FitnessEvaluator(graph, flightRepo, gates);
        for (PlaneType pt : PlaneType.values()) {
            Map<Boolean, List<Gate>> intMap = new HashMap<>();
            for (boolean intl : new boolean[]{false, true}) {
                List<Gate> exactFit = new ArrayList<>();
                List<Gate> oversizedFit = new ArrayList<>();
                for (Gate g : gates) {
                    boolean isCorrectIntl = g.isInternational() == intl;
                    boolean isLargeEnough = tempEval.isGateLargeEnough(g.getSize(), pt);
                    if (isCorrectIntl && isLargeEnough) {
                        if (tempEval.getWastedSpaceLevel(g.getSize(), pt) == 0) {
                            exactFit.add(g);
                        } else {
                            oversizedFit.add(g);
                        }
                    }
                }
                // Prefer exact-fit gates; fall back to oversized; last resort: all gates
                List<Gate> best = !exactFit.isEmpty() ? exactFit :
                                  !oversizedFit.isEmpty() ? oversizedFit :
                                  new ArrayList<>(gates);
                intMap.put(intl, best);
            }
            validGatesCache.put(pt, intMap);
        }
    }

    /**
     * Overrides the default GA parameters — mainly used for scale testing.
     */
    public void setParameters(int populationSize, double mutationRate, int maxGenerations) {
        this.populationSize = populationSize;
        this.mutationRate = mutationRate;
        this.maxGenerations = maxGenerations;
    }

    /**
     * Repairs a schedule after a new delay or conflict is injected mid-simulation.
     * Flights that have already departed or are committed stay locked — only
     * unresolved ones get re-assigned. The population seeds from the current best
     * chromosome so the GA converges quickly.
     *
     * @param baseChromosome The current schedule, possibly containing a new conflict.
     * @param lockedIndices  Flight indices whose assignments must not change.
     * @return The repaired chromosome.
     */
    public int[] runRepair(int[] baseChromosome, Set<Integer> lockedIndices) {
        this.lockedIndices = lockedIndices;
        initializeWarmPopulation(baseChromosome);
        FitnessEvaluator evaluator = new FitnessEvaluator(graph, flightRepo, gates);

        double previousBest = -Double.MAX_VALUE;
        int stagnant = 0;

        for (int i = 0; i < maxGenerations; i++) {
            evolve(evaluator);
            int[] best = getBestSolution();
            double fitness = evaluator.calculateFitness(best, flights);
            System.out.println("[Micro-GA] Generation " + i + " | Fitness: " + fitness);

            if (fitness > previousBest) {
                previousBest = fitness;
                stagnant = 0;
            } else {
                stagnant++;
            }
            if (stagnant >= MAX_STAGNANT_GENERATIONS) {
                System.out.println("[Micro-GA] Converged at generation " + i + ".");
                break;
            }
        }

        this.lockedIndices = Collections.emptySet();
        return getBestSolution().clone();
    }

    /**
     * Seeds the population from a known-good chromosome rather than random.
     * Each copy has a 30% per-gene perturbation on unlocked slots to introduce
     * diversity while keeping the solution close to optimal.
     */
    private void initializeWarmPopulation(int[] base) {
        population = new ArrayList<>();
        // Chromosome 0: exact base (the current schedule, possibly with a collision)
        population.add(base.clone());

        while (population.size() < populationSize) {
            int[] chrom = base.clone();
            for (int i = 0; i < chrom.length; i++) {
                if (!lockedIndices.contains(i) && rand.nextDouble() < 0.3) {
                    List<Gate> valid = getValidGates(flights.get(i));
                    chrom[i] = valid.get(rand.nextInt(valid.size())).getId();
                }
            }
            population.add(chrom);
        }
    }

    private void initializePopulation() {
        int greedyCount = populationSize / 10;
        GreedyInitializer greedy = new GreedyInitializer(flightRepo, gates);
        Map<Integer, Integer> greedySol = greedy.generateInitialSolution();

        int[] greedyChromosome = new int[flights.size()];
        for (int i = 0; i < flights.size(); i++) {
            Flight f = flights.get(i);
            greedyChromosome[i] = greedySol.getOrDefault(f.getId(), gates.get(0).getId());
        }

        for (int i = 0; i < greedyCount; i++) {
            population.add(greedyChromosome.clone());
        }

        while (population.size() < populationSize) {
            int[] chrom = new int[flights.size()];
            for (int i = 0; i < flights.size(); i++) {
                chrom[i] = gates.get(rand.nextInt(gates.size())).getId();
            }
            population.add(chrom);
        }
    }

    /**
     * Advances the population by one generation.
     * Fitness is computed once per chromosome per generation (cached in a double[])
     * rather than being recomputed on each sort comparison.
     * After sorting, index 0 is always the current best.
     */
    private void evolve(FitnessEvaluator evaluator) {
        int size = population.size();

        // Pre-compute fitness for every chromosome ONCE
        double[] fitnessCache = new double[size];
        for (int i = 0; i < size; i++) {
            fitnessCache[i] = evaluator.calculateFitness(population.get(i), flights);
        }

        // Sort population indices by descending fitness using the cache
        Integer[] indices = new Integer[size];
        for (int i = 0; i < size; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(fitnessCache[b], fitnessCache[a]));

        List<int[]> sortedPopulation = new ArrayList<>(size);
        for (int idx : indices) {
            sortedPopulation.add(population.get(idx));
        }
        this.population = sortedPopulation;

        List<int[]> newPopulation = new ArrayList<>();

        // Elitism: copy top ELITISM_COUNT chromosomes directly
        for (int i = 0; i < Math.min(ELITISM_COUNT, population.size()); i++) {
            newPopulation.add(population.get(i).clone());
        }

        while (newPopulation.size() < populationSize) {
            int[] parent1 = select(evaluator);
            int[] parent2 = select(evaluator);
            int[] child = crossover(parent1, parent2);
            mutate(child);
            newPopulation.add(child);
        }
        this.population = newPopulation;
    }

    private int[] select(FitnessEvaluator evaluator) {
        // BN-3: uses shared rand field
        int[] best = null;
        double bestFitness = -Double.MAX_VALUE;

        for (int i = 0; i < tournamentSize; i++) {
            int[] candidate = population.get(rand.nextInt(population.size()));
            double fitness = evaluator.calculateFitness(candidate, flights);
            if (fitness > bestFitness) {
                bestFitness = fitness;
                best = candidate;
            }
        }
        return best;
    }

    private int[] crossover(int[] p1, int[] p2) {
        // BN-3: uses shared rand field
        int n = p1.length;
        int[] child = new int[n];

        int point1 = rand.nextInt(n);
        int point2 = rand.nextInt(n);

        int start = Math.min(point1, point2);
        int end = Math.max(point1, point2);

        for (int i = 0; i < n; i++) {
            // Locked genes always come from p1 (the stable parent)
            if (lockedIndices.contains(i)) {
                child[i] = p1[i];
            } else if (i >= start && i <= end) {
                child[i] = p2[i]; // Swap segment from P2
            } else {
                child[i] = p1[i]; // Rest from P1
            }
        }
        return child;
    }

    /**
     * Returns gates that are valid for this flight's plane type and terminal.
     * Uses the pre-built cache — O(1) instead of scanning all gates each time.
     */
    private List<Gate> getValidGates(Flight f) {
        Map<Boolean, List<Gate>> byIntl = validGatesCache.get(f.getType());
        if (byIntl != null) {
            List<Gate> cached = byIntl.get(f.isInternational());
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }
        }
        return gates; // fallback (should never happen with a complete cache)
    }

    /**
     * Mutates a chromosome with conflict-directed targeting.
     * There's a 50/50 chance to mutate a flight that's already in conflict
     * rather than picking one at random — this tends to fix problems faster.
     * Falls back to random mutation when no conflicts are found.
     */
    private void mutate(int[] child) {
        if (rand.nextDouble() < mutationRate) {

            // 50% chance to do Conflict-Directed Mutation
            if (rand.nextDouble() < 0.5) {
                List<Integer> conflicts = getConflictIndices(child);
                // Filter out locked flights
                conflicts.removeIf(lockedIndices::contains);
                if (!conflicts.isEmpty()) {
                    int indexToMutate = conflicts.get(rand.nextInt(conflicts.size()));
                    Flight f = flights.get(indexToMutate);
                    List<Gate> validGates = getValidGates(f);
                    child[indexToMutate] = validGates.get(rand.nextInt(validGates.size())).getId();
                    return;
                }
            }

            // Fallback: Random mutation on any unlocked gene
            int index = rand.nextInt(child.length);
            if (lockedIndices.contains(index)) return;
            Flight f = flights.get(index);
            List<Gate> validGates = getValidGates(f);
            child[index] = validGates.get(rand.nextInt(validGates.size())).getId();
        }
    }

    /**
     * Returns the indices of flights involved in a conflict — either a time
     * overlap with another flight at the same gate, a size mismatch, or wrong terminal.
     */
    private List<Integer> getConflictIndices(int[] chromosome) {
        List<Integer> conflicts = new ArrayList<>();
        FitnessEvaluator tempEval = new FitnessEvaluator(graph, flightRepo, gates);

        for (int i = 0; i < chromosome.length; i++) {
            int gateId = chromosome[i];
            Flight f1 = flights.get(i);
            Gate gate = gateMap.get(gateId); // BN-5: O(1) lookup from field

            boolean hasConflict = false;

            if (gate != null) {
                boolean isTooSmall = !tempEval.isGateLargeEnough(gate.getSize(), f1.getType());
                boolean wrongInt = f1.isInternational() != gate.isInternational();
                boolean isWasted = tempEval.getWastedSpaceLevel(gate.getSize(), f1.getType()) > 0;

                if (isTooSmall || wrongInt || isWasted) {
                    hasConflict = true;
                }
            }

            if (!hasConflict) {
                // Time Overlap check
                for (int j = 0; j < chromosome.length && !hasConflict; j++) {
                    if (i != j && chromosome[i] == chromosome[j]) {
                        Flight f2 = flights.get(j);
                        if (f1.getArrivalTime() < f2.getDepartureTime()
                                && f2.getArrivalTime() < f1.getDepartureTime()) {
                            hasConflict = true;
                        }
                    }
                }
            }

            if (hasConflict) {
                conflicts.add(i);
            }
        }
        return conflicts;
    }

    /**
     * Returns the best chromosome. Population is sorted after each evolve() call,
     * so index 0 is always the current best.
     */
    private int[] getBestSolution() {
        return population.get(0);
    }

    public int[] run() {
        return run(null);
    }

    public interface ProgressCallback {
        void accept(List<Flight> flights, double fitness, int generation);
    }


    // Main GA method
    public int[] run(ProgressCallback onGenerationComplete) {
        initializePopulation();
        FitnessEvaluator evaluator = new FitnessEvaluator(graph, flightRepo, gates);

        double previousBestFitness = -Double.MAX_VALUE;
        int stagnantGenerations = 0;

        boolean keepRunning = true;
        for (int i = 0; i < maxGenerations && keepRunning; i++) {
            boolean interrupted = false;
            while (isPaused && !interrupted) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    interrupted = true;
                }
            }
            if (Thread.currentThread().isInterrupted() || interrupted) {
                keepRunning = false;
            }

            if (keepRunning) {
                evolve(evaluator);
                // BN-2: getBestSolution() is now O(1) — population already sorted
                int[] best = getBestSolution();
                double currentBestFitness = evaluator.calculateFitness(best, flights);
                System.out.println("Generation " + i + " | Best Fitness: " + currentBestFitness);
    
                if (onGenerationComplete != null) {
                    List<Flight> currentBestFlights = new ArrayList<>();
                    for (int j = 0; j < flights.size(); j++) {
                        Flight copy = getFlight(j, best);
                        currentBestFlights.add(copy);
                    }
                    onGenerationComplete.accept(currentBestFlights, currentBestFitness, i);
                }
    
                if (currentBestFitness > previousBestFitness) {
                    previousBestFitness = currentBestFitness;
                    stagnantGenerations = 0;
                } else {
                    stagnantGenerations++;
                }
    
                if (stagnantGenerations >= MAX_STAGNANT_GENERATIONS) {
                    System.out.println("[GA] Early stopping triggered. Converged at generation " + i + ".");
                    keepRunning = false;
                }
            }
        }

        int[] best = getBestSolution();
        double preSweepFitness = evaluator.calculateFitness(best, flights);
        System.out.println("[GA] Pre-sweep best fitness: " + preSweepFitness);

        return greedySweep(best, evaluator);
    }

    private Flight getFlight(int j, int[] best) {
        Flight clone = flights.get(j);
        Flight copy = new Flight(clone.getId(), clone.getFlightCode(), clone.getArrivalTime(),
                clone.getType(), clone.getUrgencyScore(), clone.isInternational());
        copy.setServiceDuration(clone.getServiceDuration());

        int gateId = best[j];
        copy.setAssignedGate(gates.get(gateId - 1));
        return copy;
    }

    public void pauseEngine() {
        this.isPaused = true;
    }

    public void resumeEngine() {
        this.isPaused = false;
    }

    public boolean isPaused() {
        return isPaused;
    }

    /**
     * Post-processing pass after GA convergence. Evicts any flights that still
     * violate hard constraints, then greedily re-assigns them by urgency into
     * free gate slots. Priority order: exact-size fit, then oversized, then
     * leave unassigned (which routes to HoldingState in simulation).
     *
     * Time complexity: O(N log N + N*G)
     */
    private int[] greedySweep(int[] chromosome, FitnessEvaluator evaluator) {
        int[] result = chromosome.clone();

        // Build flightId -> index map for O(1) lookup during re-assignment
        Map<Integer, Integer> flightIdToIndex = new HashMap<>();
        for (int i = 0; i < flights.size(); i++) {
            flightIdToIndex.put(flights.get(i).getId(), i);
        }

        // --- Step 1: Identify all violating flight indices ---
        Set<Integer> violatingIndices = new HashSet<>();

        // 1a. Size and international mismatches
        for (int i = 0; i < result.length; i++) {
            Gate gate = gateMap.get(result[i]);
            Flight f = flights.get(i);
            if (gate == null
                    || !evaluator.isGateLargeEnough(gate.getSize(), f.getType())
                    || f.isInternational() != gate.isInternational()) {
                violatingIndices.add(i);
            }
        }

        // 1b. Time overlaps — per gate, sort by arrival, evict the lower-urgency
        //     flight from each overlapping pair
        Map<Integer, List<Integer>> gateToIndices = new HashMap<>();
        for (int i = 0; i < result.length; i++) {
            if (!violatingIndices.contains(i)) {
                gateToIndices.computeIfAbsent(result[i], k -> new ArrayList<>()).add(i);
            }
        }
        for (List<Integer> indices : gateToIndices.values()) {
            indices.sort(Comparator.comparingInt(i -> flights.get(i).getArrivalTime()));
            int lastValidIdx = 0;
            for (int k = 1; k < indices.size(); k++) {
                Flight prev = flights.get(indices.get(lastValidIdx));
                Flight curr = flights.get(indices.get(k));
                if (curr.getArrivalTime() < prev.getDepartureTime()) {
                    // Overlap: evict the lower-urgency flight, keep the higher-urgency one
                    if (curr.getUrgencyScore() >= prev.getUrgencyScore()) {
                        violatingIndices.add(indices.get(lastValidIdx));
                        lastValidIdx = k;
                    } else {
                        violatingIndices.add(indices.get(k));
                    }
                } else {
                    lastValidIdx = k;
                }
            }
        }

        System.out.println("[Sweeper] Found " + violatingIndices.size() + " violating flights.");

        // --- Step 2: Push violating flights into FlightMinHeap, clear their slots ---
        FlightMinHeap holdingHeap = new FlightMinHeap(Math.max(violatingIndices.size() + 1, 10));
        for (int idx : violatingIndices) {
            holdingHeap.insert(flights.get(idx));
            result[idx] = -1;
        }

        // --- Step 3: Build gate occupancy from the clean (non-violating) schedule ---
        // Each entry: list of [arrivalTime, departureTime] intervals
        Map<Integer, List<int[]>> gateOccupancy = new HashMap<>();
        for (Gate g : gates) {
            gateOccupancy.put(g.getId(), new ArrayList<>());
        }
        for (int i = 0; i < result.length; i++) {
            if (result[i] > 0) {
                Flight f = flights.get(i);
                gateOccupancy.get(result[i]).add(new int[]{f.getArrivalTime(), f.getDepartureTime()});
            }
        }

        // --- Step 4: Greedy re-assignment from the holding heap ---
        int reassigned = 0;
        while (!holdingHeap.isEmpty()) {
            Flight f = holdingHeap.extractMin();
            int flightIdx = flightIdToIndex.get(f.getId());
            boolean assigned = tryAssignExactGate(f, flightIdx, result, gateOccupancy, evaluator);

            if (!assigned) {
                assigned = tryAssignOversizedGate(f, flightIdx, result, gateOccupancy, evaluator);
            }

            if (assigned) {
                reassigned++;
            } else {
                // No clean slot found — leave unassigned (sentinel 0).
                // LandedState will route this flight to HoldingState in simulation.
                result[flightIdx] = 0;
                System.out.println("[Sweeper] " + f.getFlightCode()
                        + " left unassigned — will enter HoldingState.");
            }
        }

        System.out.println("[Sweeper] Reassigned " + reassigned + " / " + violatingIndices.size() + " flights cleanly.");

        // --- Step 5: Recalculate and log post-sweep fitness ---
        double postSweepFitness = evaluator.calculateFitness(result, flights);
        System.out.println("[Sweeper] Post-sweep fitness: " + postSweepFitness);

        return result;
    }

    private boolean tryAssignExactGate(Flight f, int flightIdx, int[] result, Map<Integer, List<int[]>> gateOccupancy, FitnessEvaluator evaluator) {
        Iterator<Gate> gateIter = gates.iterator();
        boolean assigned = false;
        while (gateIter.hasNext() && !assigned) {
            Gate g = gateIter.next();
            boolean exactSize = evaluator.getWastedSpaceLevel(g.getSize(), f.getType()) == 0;
            boolean largeEnough = evaluator.isGateLargeEnough(g.getSize(), f.getType());
            boolean rightIntl = f.isInternational() == g.isInternational();
            
            if (exactSize && largeEnough && rightIntl) {
                if (isFreeSlot(gateOccupancy.get(g.getId()), f)) {
                    result[flightIdx] = g.getId();
                    gateOccupancy.get(g.getId()).add(new int[]{f.getArrivalTime(), f.getDepartureTime()});
                    assigned = true;
                }
            }
        }
        return assigned;
    }

    private boolean tryAssignOversizedGate(Flight f, int flightIdx, int[] result, Map<Integer, List<int[]>> gateOccupancy, FitnessEvaluator evaluator) {
        Iterator<Gate> gateIter = gates.iterator();
        boolean assigned = false;
        while (gateIter.hasNext() && !assigned) {
            Gate g = gateIter.next();
            boolean largeEnough = evaluator.isGateLargeEnough(g.getSize(), f.getType());
            boolean rightIntl = f.isInternational() == g.isInternational();
            
            if (largeEnough && rightIntl) {
                if (isFreeSlot(gateOccupancy.get(g.getId()), f)) {
                    result[flightIdx] = g.getId();
                    gateOccupancy.get(g.getId()).add(new int[]{f.getArrivalTime(), f.getDepartureTime()});
                    assigned = true;
                }
            }
        }
        return assigned;
    }

    private boolean forceAssignGate(Flight f, int flightIdx, int[] result, Map<Integer, List<int[]>> gateOccupancy, FitnessEvaluator evaluator) {
        Iterator<Gate> gateIter = gates.iterator();
        boolean assigned = false;
        while (gateIter.hasNext() && !assigned) {
            Gate g = gateIter.next();
            boolean largeEnough = evaluator.isGateLargeEnough(g.getSize(), f.getType());
            boolean rightIntl = f.isInternational() == g.isInternational();
            
            if (largeEnough && rightIntl) {
                result[flightIdx] = g.getId();
                gateOccupancy.get(g.getId()).add(new int[]{f.getArrivalTime(), f.getDepartureTime()});
                assigned = true;
            }
        }
        return assigned;
    }

    /**
     * Returns true if the gate has no scheduled flight overlapping this one.
     * Requires a 15-minute turnaround gap on each side, matching the greedy initializer.
     */
    private boolean isFreeSlot(List<int[]> occupancy, Flight f) {
        for (int[] interval : occupancy) {
            // interval[0] = arrivalTime, interval[1] = departureTime
            // Conflict if the new flight's window overlaps the existing window
            // including the 15-minute turnaround buffer on each departure
            if (!(f.getArrivalTime() >= interval[1] + 15 || interval[0] >= f.getDepartureTime() + 15)) {
                return false;
            }
        }
        return true;
    }

    // Test Harness
    public static void main(String[] args) {
        FlightRepository repo = new FlightRepository();
        repo.addFlight(new Flight(0, "F0", 480, model.enums.PlaneType.SMALL_BODY, 10));
        repo.addFlight(new Flight(1, "F1", 480, model.enums.PlaneType.SMALL_BODY, 10));
        repo.addFlight(new Flight(2, "F2", 480, model.enums.PlaneType.SMALL_BODY, 10));
        repo.addFlight(new Flight(3, "F3", 500, model.enums.PlaneType.LARGE_BODY, 20));
        repo.addFlight(new Flight(4, "F4", 500, model.enums.PlaneType.LARGE_BODY, 20));
        repo.addFlight(new Flight(5, "F5", 520, model.enums.PlaneType.JUMBO_BODY, 30));
        repo.addFlight(new Flight(6, "F6", 520, model.enums.PlaneType.JUMBO_BODY, 30));
        repo.addFlight(new Flight(7, "F7", 540, model.enums.PlaneType.SMALL_BODY, 5));
        repo.addFlight(new Flight(8, "F8", 600, model.enums.PlaneType.LARGE_BODY, 15));
        repo.addFlight(new Flight(9, "F9", 600, model.enums.PlaneType.JUMBO_BODY, 25));

        List<Gate> gates = new ArrayList<>();
        gates.add(new Gate(1, model.enums.GateSize.SIZE_SMALL, 0, 0));
        gates.add(new Gate(2, model.enums.GateSize.SIZE_LARGE, 10, 0));
        gates.add(new Gate(3, model.enums.GateSize.SIZE_JUMBO, 20, 0));

        TerminalGraph graph = new TerminalGraph();
        for (Gate g : gates)
            graph.addGate(g);
        graph.connectGates(gates.get(0), gates.get(1));
        graph.connectGates(gates.get(1), gates.get(2));

        graph.initializeDistanceMatrix();
        System.out.println("Starting Genetic Algorithm...");
        GeneticEngine engine = new GeneticEngine(repo, gates, graph);
        // You can use test tuning here if wanted:
        // engine.setParameters(200, 0.05, 100);
        int[] solution = engine.run();

        System.out.println("Final Solution:");
        for (int i = 0; i < solution.length; i++) {
            System.out.println("Flight " + i + " -> Gate " + solution[i]);
        }

        FitnessEvaluator evaluator = new FitnessEvaluator(graph, repo, gates);
        System.out.println(
                "Final Fitness: " + evaluator.calculateFitness(solution, new ArrayList<>(repo.getAllFlights())));
    }
}
