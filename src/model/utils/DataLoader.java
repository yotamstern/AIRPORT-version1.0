package model.utils;

import model.Flight;
import model.FlightRepository;
import model.enums.PlaneType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Utility class to load flight data from external sources (CSV).
 */
public class DataLoader {

    /**
     * Loads flights from a CSV file into the repository.
     * Expected CSV Format: ID,FlightCode,ArrivalTime,PlaneType,UrgencyScore
     *
     * @param repo     The repository to populate.
     * @param filePath The path to the CSV file.
     */
    public static void loadFlights(FlightRepository repo, String filePath) {
        String line = "";
        String cvsSplitBy = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {

            // Skip header
            br.readLine();

            while ((line = br.readLine()) != null) {
                // Use comma as separator
                String[] data = line.split(cvsSplitBy);

                if (data.length >= 5) {
                    try {
                        int id = Integer.parseInt(data[0].trim());
                        String code = data[1].trim();
                        int arrivalTime = Integer.parseInt(data[2].trim());
                        PlaneType type = PlaneType.valueOf(data[3].trim());
                        double urgency = Double.parseDouble(data[4].trim());

                        Flight flight = new Flight(id, code, arrivalTime, type, urgency);
                        repo.addFlight(flight);

                    } catch (IllegalArgumentException e) {
                        System.err.println("Skipping invalid line: " + line + " Error: " + e.getMessage());
                    }
                }
            }
            System.out.println("Data loading complete.");

        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }

    /**
     * Test Harness for Data Loading
     */
    public static void main(String[] args) {
        FlightRepository repo = new FlightRepository();
        String filePath = "flights.csv"; // Assuming run from project root

        System.out.println("Loading flights from " + filePath + "...");
        loadFlights(repo, filePath);

        System.out.println("Successfully loaded " + repo.getAllFlights().size() + " flights.");

        // Verify a sample
        if (repo.getFlight(1) != null) {
            System.out.println("Sample Verification: Flight 1 loaded -> " + repo.getFlight(1).getFlightCode());
        }
    }
}
