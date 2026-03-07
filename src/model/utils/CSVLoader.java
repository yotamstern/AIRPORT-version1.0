package model.utils;

import model.Flight;
import model.enums.PlaneType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CSVLoader {

    public static Map<String, Flight> loadFlights(String filePath) {
        Map<String, Flight> flightsMap = new HashMap<>();
        String line = "";
        String cvsSplitBy = ",";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            // Skip header
            br.readLine();

            int idCounter = 1;

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

                        flightsMap.put(flightCode, flight);

                    } catch (IllegalArgumentException e) {
                        System.err.println("Skipping invalid line: " + line + " Error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
        }

        return flightsMap;
    }
}
