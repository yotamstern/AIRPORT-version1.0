package model.algo.genetic;

import model.Flight;
import java.util.List;

/**
 * Utility for sanity-checking schedules outside of the fitness function.
 * Mainly used in tests to verify a final solution has no gate collisions.
 */
public class ConstraintChecker {

    /**
     * Returns the number of gate-time collisions in a schedule.
     * O(N^2) — sorting to O(N log N) isn't worth it here since this is only
     * called for verification, not during GA evolution.
     *
     * @param chromosome Index-to-gateId mapping (same encoding as the GA chromosome).
     * @param flights    Flight list, indexed to match the chromosome.
     * @return Number of hard constraint violations found.
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
                    int end1 = start1 + 60; // 60 min turnaround

                    int start2 = f2.getArrivalTime();
                    int end2 = start2 + 60; // 60 min turnaround

                    if (start1 < end2 && start2 < end1) {
                        collisions++;
                    }
                }
            }
        }
        return collisions;
    }
}
