package model.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

/**
 * Generates a realistic synthetic flight schedule and writes it to a CSV file.
 *
 * <p>Flights are distributed across the day from {@code DAY_START_MINUTE} (06:00) to
 * {@code DAY_END_MINUTE} (23:00), with random arrival variance so they don't land in
 * perfectly even intervals. Each flight is randomly assigned to a size/terminal category
 * using the ratio constants, then shuffled so different aircraft sizes are spread
 * organically throughout the day rather than grouped by type.
 *
 * <p>A fraction of flights also receive connecting-passenger transfer records, simulating
 * passengers who need to walk between gates to catch a second flight.
 *
 * <p>Run this class directly to regenerate {@code flights.csv} before launching the simulation.
 */
public class FlightCSVGenerator {

    private static final int NUM_FLIGHTS = 250;
    private static final int DAY_START_MINUTE = 360;   // 06:00
    private static final int DAY_END_MINUTE = 1380;    // 23:00
    private static final double SMALL_BODY_RATIO = 0.50;
    private static final double LARGE_BODY_RATIO = 0.30;
    // Implied JUMBO_BODY_RATIO = 1 - SMALL_BODY_RATIO - LARGE_BODY_RATIO = 0.20
    private static final double SMALL_DOM_RATIO = 0.66;
    private static final double LARGE_DOM_RATIO = 0.55;
    private static final int ARRIVAL_VARIANCE_MINUTES = 25;
    private static final int DEPARTURE_VARIANCE_MINUTES = 5;
    private static final double TRANSFER_PROBABILITY = 0.5;
    private static final int MAX_TRANSFERS_PER_FLIGHT = 3;
    private static final int MAX_TRANSFER_PASSENGERS = 20;

    public static void main(String[] args) {
        String filename = "flights.csv";
        int numFlights = NUM_FLIGHTS;
        Random rand = new Random();
        boolean isInternational;
        String[] airlines = {"LY", "DL", "LH", "UA", "BA"};

        try (FileWriter writer = new FileWriter(filename)) {
            // Write CSV header
            writer.write("FlightCode,PlaneSize,IsInternational,ArrivalMinute,DepartureMinute,PassengerCount,Transfers\n");

            // Calculate per-category flight counts from the ratio constants.
            // Integer casting truncates rather than rounds, so the total may fall 1-2 short
            // of numFlights. We absorb the rounding error into numSmallDom (the largest bucket).
            int numSmallDom = (int) (numFlights * SMALL_BODY_RATIO * SMALL_DOM_RATIO);
            int numSmallInt = (int) (numFlights * SMALL_BODY_RATIO * (1 - SMALL_DOM_RATIO));
            int numLargeDom = (int) (numFlights * LARGE_BODY_RATIO * LARGE_DOM_RATIO);
            int numLargeInt = (int) (numFlights * LARGE_BODY_RATIO * (1 - LARGE_DOM_RATIO));
            int numJumboInt = (int) (numFlights * (1 - SMALL_BODY_RATIO - LARGE_BODY_RATIO));

            int assignedCount = numSmallDom + numSmallInt + numLargeDom + numLargeInt + numJumboInt;
            numSmallDom += (numFlights - assignedCount); // correct any rounding shortfall

            java.util.List<String[]> flightSpecs = new java.util.ArrayList<>();
            // Format: PlaneSize, IsInternational, BaseTurnaround, MinPax, MaxPax
            for (int i = 0; i < numSmallDom; i++) flightSpecs.add(new String[]{"SMALL_BODY", "false", "40", "50", "150"});
            for (int i = 0; i < numSmallInt; i++) flightSpecs.add(new String[]{"SMALL_BODY", "true", "40", "50", "150"});
            for (int i = 0; i < numLargeDom; i++) flightSpecs.add(new String[]{"LARGE_BODY", "false", "55", "150", "280"});
            for (int i = 0; i < numLargeInt; i++) flightSpecs.add(new String[]{"LARGE_BODY", "true", "55", "150", "280"});
            for (int i = 0; i < numJumboInt; i++) flightSpecs.add(new String[]{"JUMBO_BODY", "true", "75", "280", "500"});

            // Shuffle the specs so sizes are randomly distributed throughout the day
            java.util.Collections.shuffle(flightSpecs, rand);

            int totalMinutes = DAY_END_MINUTE - DAY_START_MINUTE;
            double minutesPerFlight = (double) totalMinutes / numFlights;

            // Pre-generate all valid FlightCodes first so Transfers can reference them
            String[] generatedCodes = new String[numFlights];
            for (int i = 0; i < numFlights; i++) {
                String airline = airlines[rand.nextInt(airlines.length)];
                generatedCodes[i] = String.format("%s-%03d", airline, i + 1);
            }

            for (int i = 0; i < numFlights; i++) {
                // Generate Flight Code with Airline Prefix (e.g., UA-001)
                String flightCode = generatedCodes[i];
                
                String[] spec = flightSpecs.get(i);
                String planeSize = spec[0];
                isInternational = Boolean.parseBoolean(spec[1]);
                int baseTurnaround = Integer.parseInt(spec[2]);
                int minPax = Integer.parseInt(spec[3]);
                int maxPax = Integer.parseInt(spec[4]);
                int passengerCount = minPax + rand.nextInt(maxPax - minPax + 1);

                // Spread arrival times across the day with organic variance
                int baseArrival = DAY_START_MINUTE + (int)(i * minutesPerFlight);
                int timeVariance = rand.nextInt(ARRIVAL_VARIANCE_MINUTES * 2 + 1) - ARRIVAL_VARIANCE_MINUTES;
                int arrivalMinute = Math.max(DAY_START_MINUTE, Math.min(DAY_END_MINUTE, baseArrival + timeVariance));

                // Departure variance to avoid perfectly aligned turnarounds
                int variance = rand.nextInt(DEPARTURE_VARIANCE_MINUTES * 2 + 1) - DEPARTURE_VARIANCE_MINUTES;
                int departureMinute = arrivalMinute + baseTurnaround + variance;

                // Ensure Departure > Arrival just in case
                if (departureMinute <= arrivalMinute) {
                    departureMinute = arrivalMinute + 1;
                }

                // Generate random transfers for a fraction of flights
                StringBuilder transfersStr = new StringBuilder();
                if (rand.nextDouble() < TRANSFER_PROBABILITY) {
                    int numTransfers = rand.nextInt(MAX_TRANSFERS_PER_FLIGHT) + 1;
                    for (int j = 0; j < numTransfers; j++) {
                        // Pick a random target flight that is NOT this flight
                        int targetIdx = rand.nextInt(numFlights);
                        while (targetIdx == i) {
                            targetIdx = rand.nextInt(numFlights);
                        }
                        // get the exact target code from the pre-generated array
                        String targetCode = generatedCodes[targetIdx];
                        int numPax = rand.nextInt(MAX_TRANSFER_PASSENGERS) + 1;
                        
                        transfersStr.append(targetCode).append(":").append(numPax);
                        if (j < numTransfers - 1) {
                            transfersStr.append(";");
                        }
                    }
                }

                // Write the flight record to the CSV file
                writer.write(String.format("%s,%s,%b,%d,%d,%d,%s\n",
                        flightCode, planeSize, isInternational, arrivalMinute, departureMinute, passengerCount, transfersStr.toString()));
            }

            System.out.println("Successfully generated " + numFlights + " flights to " + filename);

        } catch (IOException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }
}
