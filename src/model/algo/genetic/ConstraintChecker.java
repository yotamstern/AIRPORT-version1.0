package model.algo.genetic;

import model.Flight;
import java.util.List;

/**
 * Helper class to verify constraints in the schedule.
 * Specifically checks for gate collisions where two flights occupy the same
 * gate at the same time.
 */
public class ConstraintChecker {

    /**
     * Counts the number of collisions in a given schedule.
     * A collision is defined as two flights assigned to the same gate with
     * overlapping time windows.
     * <p>
     * <b>Time Complexity:</b> O(N^2), where N is the number of flights.
     * We iterate through every pair of flights to check for overlaps.
     * While O(N log N) is possible with sorting, O(N^2) is acceptable for small N
     * in this project phase.
     * </p>
     * 
     * @param chromosome The schedule representation (index = flight index, value =
     *                   gate ID).
     * @param flights    The list of flights (indexed by position in chromosome).
     * @return The number of hard constraint violations.
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
