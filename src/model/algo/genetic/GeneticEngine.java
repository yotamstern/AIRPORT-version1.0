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
 */
public class GeneticEngine {
    private FlightRepository flightRepo;
    private List<Gate> gates;
    private TerminalGraph graph;

    private List<Flight> flights;
    private List<int[]> population;

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
    }

    /**
     * Parameter Tuning method for scale testing (Task 3).
     */
    public void setParameters(int populationSize, double mutationRate, int maxGenerations) {
        this.populationSize = populationSize;
        this.mutationRate = mutationRate;
        this.maxGenerations = maxGenerations;
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

        Random rand = new Random();
        while (population.size() < populationSize) {
            int[] chrom = new int[flights.size()];
            for (int i = 0; i < flights.size(); i++) {
                chrom[i] = gates.get(rand.nextInt(gates.size())).getId();
            }
            population.add(chrom);
        }
    }

    private void evolve(FitnessEvaluator evaluator) {
        List<int[]> newPopulation = new ArrayList<>();

        // Elitism (Task 2): Copy the top ELITISM_COUNT performing chromosomes directly
        // to the next generation
        // without any crossover or mutation to preserve the absolute best schemas.
        population.sort((c1, c2) -> Double.compare(
                evaluator.calculateFitness(c2, flights),
                evaluator.calculateFitness(c1, flights)));

        for (int i = 0; i < Math.min(ELITISM_COUNT, population.size()); i++) {
            newPopulation.add(population.get(i).clone());
        }

        while (newPopulation.size() < populationSize) {
            int[] parent1 = select(evaluator);
            int[] parent2 = select(evaluator);
            int[] child = crossover(parent1, parent2);
            mutate(child, evaluator); // Updated to pass evaluator for conflict checking
            newPopulation.add(child);
        }
        this.population = newPopulation;
    }

    private int[] select(FitnessEvaluator evaluator) {
        Random rand = new Random();
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
     * Conflict-Directed Mutation (Task 2):
     * Instead of purely random mutation, there is a 50% chance to target a flight
     * that is currently involved in a time or size conflict. If no conflicts exist,
     * or the 50% roll fails, it falls back to completely random mutation.
     */
    private void mutate(int[] child, FitnessEvaluator evaluator) {
        Random rand = new Random();
        if (rand.nextDouble() < mutationRate) {

            // 50% chance to do Conflict-Directed Mutation
            if (rand.nextDouble() < 0.5) {
                List<Integer> conflicts = getConflictIndices(child, evaluator);
                if (!conflicts.isEmpty()) {
                    // Pick a random flight that has a conflict and re-assign it
                    int indexToMutate = conflicts.get(rand.nextInt(conflicts.size()));
                    child[indexToMutate] = gates.get(rand.nextInt(gates.size())).getId();
                    return; // Successfully performed targeted mutation
                }
            }

            // Fallback: Random mutation on any gene
            int index = rand.nextInt(child.length);
            child[index] = gates.get(rand.nextInt(gates.size())).getId();
        }
    }

    /**
     * Finds flights that are in conflict (either overlapping time or incorrect gate
     * size).
     */
    private List<Integer> getConflictIndices(int[] chromosome, FitnessEvaluator evaluator) {
        List<Integer> conflicts = new ArrayList<>();
        Map<Integer, Gate> gateMap = new HashMap<>();
        for (Gate g : gates)
            gateMap.put(g.getId(), g);

        for (int i = 0; i < chromosome.length; i++) {
            int gateId = chromosome[i];
            Flight f1 = flights.get(i);
            Gate gate = gateMap.get(gateId);

            boolean hasConflict = false;

            // Size Mismatch check
            if (gate != null && !evaluator.isGateLargeEnough(gate.getSize(), f1.getType())) {
                hasConflict = true;
            } else {
                // Time Overlap check
                for (int j = 0; j < chromosome.length; j++) {
                    if (i != j && chromosome[i] == chromosome[j]) {
                        Flight f2 = flights.get(j);
                        // Two flights overlap on the same gate if Start1 < End2 AND Start2 < End1
                        if (f1.getArrivalTime() < f2.getDepartureTime()
                                && f2.getArrivalTime() < f1.getDepartureTime()) {
                            hasConflict = true;
                            break;
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
        FitnessEvaluator evaluator = new FitnessEvaluator(graph, flightRepo, gates);

        double previousBestFitness = -Double.MAX_VALUE;
        int stagnantGenerations = 0;

        for (int i = 0; i < maxGenerations; i++) {
            evolve(evaluator);
            int[] best = getBestSolution(evaluator);
            double currentBestFitness = evaluator.calculateFitness(best, flights);
            System.out.println("Generation " + i + " | Best Fitness: " + currentBestFitness);

            if (currentBestFitness > previousBestFitness) {
                previousBestFitness = currentBestFitness;
                stagnantGenerations = 0;
            } else {
                stagnantGenerations++;
            }

            if (stagnantGenerations >= MAX_STAGNANT_GENERATIONS) {
                System.out.println("[GA] Early stopping triggered. Converged at generation " + i + ".");
                break;
            }
        }

        return getBestSolution(evaluator);
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
