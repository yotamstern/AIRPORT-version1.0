package model.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class FlightCSVGenerator {

    public static void main(String[] args) {
        String filename = "flights.csv";
        int numFlights = 300;
        Random rand = new Random();

        try (FileWriter writer = new FileWriter(filename)) {
            // Write CSV header
            writer.write("FlightCode,PlaneSize,IsInternational,ArrivalMinute,DepartureMinute\n");

            for (int i = 1; i <= numFlights; i++) {
                // Generate Flight Code (FL-001 to FL-300)
                String flightCode = String.format("FL-%03d", i);

                // Generate Plane Size and associate base turnaround time
                int sizeRoll = rand.nextInt(100);
                String planeSize;
                int baseTurnaround;
                if (sizeRoll < 70) {
                    planeSize = "SMALL_BODY"; // 70% chance
                    baseTurnaround = 40;
                } else if (sizeRoll < 90) {
                    planeSize = "LARGE_BODY"; // 20% chance
                    baseTurnaround = 55;
                } else {
                    planeSize = "JUMBO_BODY"; // 10% chance
                    baseTurnaround = 75;
                }

                // Generate International Status (30% chance International, 70% Domestic)
                boolean isInternational = rand.nextInt(100) < 30;

                // Generate Arrival Time (Random integer between 360 and 1380)
                int arrivalMinute = rand.nextInt(1380 - 360 + 1) + 360;

                // Generate Departure Time with variance (-5 to +5 minutes)
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
