package model.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class FlightCSVGenerator {

    public static void main(String[] args) {
        String filename = "flights.csv";
        int numFlights = 250;
        Random rand = new Random();
        boolean isInternational;

        try (FileWriter writer = new FileWriter(filename)) {
            // Write CSV header
            writer.write("FlightCode,PlaneSize,IsInternational,ArrivalMinute,DepartureMinute\n");

            // Calculate exact quotas based on 250 flights
            int numSmallDom = (int) (numFlights * 0.50 * 0.66); // 15 Small Gates * 66% Dom = ~83
            int numSmallInt = (int) (numFlights * 0.50 * 0.34); // 15 Small Gates * 34% Int = ~42
            int numLargeDom = (int) (numFlights * 0.30 * 0.55); // 9 Large Gates * 55% Dom = ~41
            int numLargeInt = (int) (numFlights * 0.30 * 0.45); // 9 Large Gates * 45% Int = ~34
            int numJumboInt = (int) (numFlights * 0.20);        // 6 Jumbo Gates * 100% Int = 50
            
            // Adjust for any integer rounding losses to ensure we hit exactly numFlights
            int assignedCount = numSmallDom + numSmallInt + numLargeDom + numLargeInt + numJumboInt;
            numSmallDom += (numFlights - assignedCount);

            java.util.List<String[]> flightSpecs = new java.util.ArrayList<>();
            for (int i = 0; i < numSmallDom; i++) flightSpecs.add(new String[]{"SMALL_BODY", "false", "40"});
            for (int i = 0; i < numSmallInt; i++) flightSpecs.add(new String[]{"SMALL_BODY", "true", "40"});
            for (int i = 0; i < numLargeDom; i++) flightSpecs.add(new String[]{"LARGE_BODY", "false", "55"});
            for (int i = 0; i < numLargeInt; i++) flightSpecs.add(new String[]{"LARGE_BODY", "true", "55"});
            for (int i = 0; i < numJumboInt; i++) flightSpecs.add(new String[]{"JUMBO_BODY", "true", "75"});

            // Shuffle the specs so sizes are randomly distributed throughout the day
            java.util.Collections.shuffle(flightSpecs, rand);

            // Time distribution logic (spread evenly over 1020 mins to prevent mathematical rush hours)
            int totalMinutes = 1380 - 360; // 17 hours
            double minutesPerFlight = (double) totalMinutes / numFlights;

            for (int i = 0; i < numFlights; i++) {
                // Generate Flight Code (FL-001 to FL-250)
                String flightCode = String.format("FL-%03d", i + 1);
                
                String[] spec = flightSpecs.get(i);
                String planeSize = spec[0];
                isInternational = Boolean.parseBoolean(spec[1]);
                int baseTurnaround = Integer.parseInt(spec[2]);

                // Generate Spread Arrival Time (with +/- 25 min variance to keep it looking organic)
                int baseArrival = 360 + (int)(i * minutesPerFlight);
                int timeVariance = rand.nextInt(51) - 25; // -25 to +25
                int arrivalMinute = Math.max(360, Math.min(1380, baseArrival + timeVariance));

                // Generate Departure Time with variance (-5 to +5 minutes)
                // Note: Jumbo turnaround relies on this to not perfectly line up 75+75...
                int variance = rand.nextInt(11) - 5;
                int departureMinute = arrivalMinute + baseTurnaround + variance;

                // Ensure Departure > Arrival just in case
                if (departureMinute <= arrivalMinute) {
                    departureMinute = arrivalMinute + 1;
                }

                // Write the flight record to the CSV file
                writer.write(String.format("%s,%s,%b,%d,%d\n",
                        flightCode, planeSize, isInternational, arrivalMinute, departureMinute));
            }

            System.out.println("Successfully generated " + numFlights + " flights to " + filename);

        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }
}
