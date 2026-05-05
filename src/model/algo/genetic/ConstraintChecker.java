package model.algo.genetic;

import model.Flight;
import java.util.List;

/**
 * A simple correctness checker used by tests to verify a final schedule has no
 * gate collisions. This is intentionally separate from {@link FitnessEvaluator} —
 * the evaluator is optimised for speed during GA evolution, while this class
 * prioritises clarity and is only called once at the end for a sanity check.
 */
public class ConstraintChecker {

    // The GA uses real departure times from flight data; this checker uses a fixed
    // turnaround as a conservative stand-in when testing without full flight objects
    private static final int DEFAULT_TURNAROUND_MINUTES = 60;

    /**
     * Counts how many pairs of flights share a gate and overlap in time.
     * Uses an O(N²) brute-force scan — acceptable here because this is only
     * called for final verification, never during the hot GA evolution loop.
     *
     * @param chromosome gate assignment array (chromosome[i] = gate ID for flight i)
     * @param flights    flight list, ordered the same way as the chromosome
     * @return number of hard time-overlap violations found
     */
    public static int countCollisions(int[] chromosome, List<Flight> flights) {
        int collisions = 0;
        int n = chromosome.length;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                // Check if assigned to the same gate
                if (chromosome[i] == chromosome[j]) {
                    Flight f1 = flights.get(i);
                    Flight f2 = flights.get(j);

                    // Check for time overlap
                    // Overlap logic: Start1 < End2 AND Start2 < End1
                    // Assuming duration is 45 mins for simplicity if not specified,
                    // OR we just use a fixed buffer. Requirement said: "startA < endB && startB <
                    // endA"
                    // Flight has arrivalTime. Let's assume departure is arrival + 60 mins for
                    // turnaround.

                    int start1 = f1.getArrivalTime();
                    int end1 = start1 + DEFAULT_TURNAROUND_MINUTES;

                    int start2 = f2.getArrivalTime();
                    int end2 = start2 + DEFAULT_TURNAROUND_MINUTES;

                    if (start1 < end2 && start2 < end1) {
                        collisions++;
                    }
                }
            }
        }
        return collisions;
    }
}
