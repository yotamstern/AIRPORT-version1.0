package model.algo.genetic;

import model.Flight;
import model.FlightRepository;
import model.Gate;
import model.algo.GreedyInitializer;
import model.spatial.TerminalGraph;

import java.util.*;

/**
 * The core engine of the Genetic Algorithm.
 * Evolves a population of schedules to find an optimal gate assignment.
 * <p>
 * <b>Complexity Analysis:</b>
 * - Initialization: O(P * N log N) where P is population size (due to
 * sorting/greedy).
 * - Evolution Loop: Runs G generations.
 * - Fitness Eval: O(P * N^2) (Collision Check is dominant).
 * - Selection/Crossover/Mutation: O(P * N).
 * - Total: O(G * P * N^2).
 * </p>
 */
public class GeneticEngine {
    private FlightRepository flightRepo;
    private List<Gate> gates;
    private TerminalGraph graph;

    private List<Flight> flights; // Cached list for index mapping
    private List<int[]> population;

    // GA Parameters
    private static final int POPULATION_SIZE = 100;
    private static final double MUTATION_RATE = 0.05;
    private static final int MAX_GENERATIONS = 50;
    private static final int TOURNAMENT_SIZE = 5;

    public GeneticEngine(FlightRepository flightRepo, List<Gate> gates, TerminalGraph graph) {
        this.flightRepo = flightRepo;
        this.gates = gates;
        this.graph = graph;
        this.flights = new ArrayList<>(flightRepo.getAllFlights());
        this.population = new ArrayList<>();
    }

    /**
     * Initializes the population with a hybrid approach.
     * 10% Greedy solutions, 90% Random solutions.
     */
    private void initializePopulation() {
        // 1. Greedy Injection (10%)
        int greedyCount = POPULATION_SIZE / 10;
        GreedyInitializer greedy = new GreedyInitializer(flightRepo, gates);
        Map<Integer, Integer> greedySol = greedy.generateInitialSolution();

        int[] greedyChromosome = new int[flights.size()];
        for (int i = 0; i < flights.size(); i++) {
            Flight f = flights.get(i);
            greedyChromosome[i] = greedySol.getOrDefault(f.getId(), gates.get(0).getId()); // Fallback
        }

        for (int i = 0; i < greedyCount; i++) {
            population.add(greedyChromosome.clone()); // Add copies
        }

        // 2. Random Fill (90%)
        Random rand = new Random();
        while (population.size() < POPULATION_SIZE) {
            int[] chrom = new int[flights.size()];
            for (int i = 0; i < flights.size(); i++) {
                chrom[i] = gates.get(rand.nextInt(gates.size())).getId();
            }
            population.add(chrom);
        }
    }

    /**
     * Runs the evolution process for one generation.
     */
    private void evolve() {
        List<int[]> newPopulation = new ArrayList<>();
        FitnessEvaluator evaluator = new FitnessEvaluator(graph, flightRepo);

        // Elitism: Keep best solution? (Optional, but good practice)
        int[] best = getBestSolution(evaluator);
        newPopulation.add(best);

        while (newPopulation.size() < POPULATION_SIZE) {
            // Selection
            int[] parent1 = select(evaluator);
            int[] parent2 = select(evaluator);

            // Crossover
            int[] child = crossover(parent1, parent2);

            // Mutation
            mutate(child);

            newPopulation.add(child);
        }
        this.population = newPopulation;
    }

    /**
     * Tournament Selection.
     * Picks K individuals at random and returns the best one.
     */
    private int[] select(FitnessEvaluator evaluator) {
        Random rand = new Random();
        int[] best = null;
        double bestFitness = -1;

        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            int[] candidate = population.get(rand.nextInt(population.size()));
            double fitness = evaluator.calculateFitness(candidate, flights);
            if (fitness > bestFitness) {
                bestFitness = fitness;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Two-Point Crossover.
     * Selects two points and swaps the middle segment.
     */
    private int[] crossover(int[] p1, int[] p2) {
        int n = p1.length;
        int[] child = new int[n];
        Random rand = new Random();

        int point1 = rand.nextInt(n);
        int point2 = rand.nextInt(n);

        int start = Math.min(point1, point2);
        int end = Math.max(point1, point2);

        for (int i = 0; i < n; i++) {
            if (i >= start && i <= end) {
                child[i] = p2[i]; // Swap segment from P2
            } else {
                child[i] = p1[i]; // Rest from P1
            }
        }
        return child;
    }

    /**
     * Mutates the child by randomly reassigning a flight to a random gate.
     */
    private void mutate(int[] child) {
        Random rand = new Random();
        if (rand.nextDouble() < MUTATION_RATE) {
            int index = rand.nextInt(child.length);
            child[index] = gates.get(rand.nextInt(gates.size())).getId();
        }
    }

    private int[] getBestSolution(FitnessEvaluator evaluator) {
        int[] best = population.get(0);
        double bestFit = evaluator.calculateFitness(best, flights);

        for (int[] ind : population) {
            double fit = evaluator.calculateFitness(ind, flights);
            if (fit > bestFit) {
                bestFit = fit;
                best = ind;
            }
        }
        return best;
    }

    public int[] run() {
        initializePopulation();
        FitnessEvaluator evaluator = new FitnessEvaluator(graph, flightRepo);

        for (int i = 0; i < MAX_GENERATIONS; i++) {
            evolve();
            int[] best = getBestSolution(evaluator);
            double fit = evaluator.calculateFitness(best, flights);
            System.out.println("Generation " + i + " | Best Fitness: " + fit);
        }

        return getBestSolution(evaluator);
    }

    // Test Harness
    public static void main(String[] args) {
        // 1. Setup Data
        FlightRepository repo = new FlightRepository();
        // Create 10 dummy flights with overlapping times to force collisions if not
        // optimized
        // Gate 1: Small, Gate 2: Large, Gate 3: Jumbo
        repo.addFlight(new Flight(0, "F0", 480, model.enums.PlaneType.SMALL_BODY, 10));
        repo.addFlight(new Flight(1, "F1", 480, model.enums.PlaneType.SMALL_BODY, 10)); // Collision with F0 if same
                                                                                        // gate
        repo.addFlight(new Flight(2, "F2", 480, model.enums.PlaneType.SMALL_BODY, 10)); // Collision with F0/F1 if same
                                                                                        // gate
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
        // Connect gates to allow distance calculation (linear chain for simplicity)
        graph.connectGates(gates.get(0), gates.get(1));
        graph.connectGates(gates.get(1), gates.get(2));

        // 2. Run Engine
        System.out.println("Starting Genetic Algorithm...");
        GeneticEngine engine = new GeneticEngine(repo, gates, graph);
        int[] solution = engine.run();

        // 3. Print Results
        System.out.println("Final Solution:");
        for (int i = 0; i < solution.length; i++) {
            System.out.println("Flight " + i + " -> Gate " + solution[i]);
        }

        // 4. Verification
        int collisions = ConstraintChecker.countCollisions(solution, new ArrayList<>(repo.getAllFlights()));
        System.out.println("Final Collisions: " + collisions);
        if (collisions == 0) {
            System.out.println("SUCCESS: Optimized schedule found with 0 collisions.");
        } else {
            System.out.println("WARNING: Solution has remaining collisions.");
        }
    }
}
