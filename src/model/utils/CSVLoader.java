package model.utils;

import model.Flight;
import model.FlightRepository;
import model.Transfer;
import model.enums.PlaneType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CSVLoader {

    public static void loadFlights(String filePath, FlightRepository repo) {
        String line = "";
        String cvsSplitBy = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            // Skip header
            br.readLine();

            int idCounter = 1;

            // Structure to hold transfer strings to be processed after flights are created
            // Map<FlightId, List of transfer strings>
            Map<Integer, String[]> pendingTransfers = new HashMap<>();
            // Maintain a code -> id mapping to resolve transfers
            Map<String, Integer> flightCodeToId = new HashMap<>();

            while ((line = br.readLine()) != null) {
                String[] data = line.split(cvsSplitBy);

                if (data.length >= 5) {
                    try {
                        String flightCode = data[0].trim();
                        PlaneType type = PlaneType.valueOf(data[1].trim());
                        boolean isInternational = Boolean.parseBoolean(data[2].trim());
                        int arrivalMinute = Integer.parseInt(data[3].trim());
                        int departureMinute = Integer.parseInt(data[4].trim());

                        // Assuming default urgency score of 0.0
                        Flight flight = new Flight(idCounter++, flightCode, arrivalMinute, type, 0.0, isInternational);

                        // Set the correct service duration based on the parsed departure - arrival
                        flight.setServiceDuration(departureMinute - arrivalMinute);

                        // Parse passenger count (6th column)
                        if (data.length >= 6) {
                            try {
                                flight.setPassengerCount(Integer.parseInt(data[5].trim()));
                            } catch (NumberFormatException e) {
                                // leave default 0 if column is missing or malformed
                            }
                        }

                        repo.addFlight(flight);
                        flightCodeToId.put(flightCode, flight.getId());

                        // If there is transfer data (7th column)
                        if (data.length >= 7 && !data[6].trim().isEmpty()) {
                            String[] transferData = data[6].trim().split(";");
                            pendingTransfers.put(flight.getId(), transferData);
                        }

                    } catch (IllegalArgumentException e) {
                        System.err.println("Skipping invalid line: " + line + " Error: " + e.getMessage());
                    }
                }
            }

            // Second pass: Resolve transfers now that all flights have IDs
            for (Map.Entry<Integer, String[]> entry : pendingTransfers.entrySet()) {
                int fromFlightId = entry.getKey();
                for (String tString : entry.getValue()) {
                    String[] tData = tString.split(":");
                    if (tData.length == 2) {
                        String targetFlightCode = tData[0].trim();
                        try {
                            int numPax = Integer.parseInt(tData[1].trim());
                            Integer toFlightId = flightCodeToId.get(targetFlightCode);
                            if (toFlightId != null) {
                                repo.addTransfer(new Transfer(fromFlightId, toFlightId, numPax));
                            } else {
                                System.err.println("Warning: Transfer target flight code not found: " + targetFlightCode);
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("Warning: Invalid passenger count in transfer data: " + tString);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
        }
    }
}
