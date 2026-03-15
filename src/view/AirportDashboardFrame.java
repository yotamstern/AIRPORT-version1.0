package view;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import model.Flight;
import model.FlightRepository;
import model.Gate;
import model.algo.genetic.FitnessEvaluator;
import model.algo.genetic.GeneticEngine;
import model.enums.GateSize;
import model.enums.PlaneType;
import model.spatial.TerminalGraph;

public class AirportDashboardFrame extends JFrame {

    // --- Unified Color Palette ---
    private static final Color BG_MAIN = new Color(30, 33, 43); // #1e212b
    private static final Color BG_PANEL = new Color(42, 46, 57); // #2a2e39
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255); // #ffffff
    private static final Color TEXT_SECONDARY = new Color(160, 165, 177);// #a0a5b1
    private static final Color BTN_BLUE = new Color(59, 130, 246); // #3b82f6
    private static final Color BTN_RED_ = new Color(239, 68, 68); // #ef4444
    private static final Color BTN_BASIC = new Color(55, 65, 81); // #374151

    // UI References
    private JLabel lblTotalFlights = new JLabel("");
    private JLabel lblAssigned = new JLabel("0");
    private JLabel lblHolding = new JLabel("0");
    private JLabel lblFitness = new JLabel("0");
    private JLabel lblGeneration = new JLabel("0");

    private JButton btnStartSimulation;
    private JButton btnPause;
    private JButton btnReset;

    private GanttChartPanel ganttChartPanel;
    private JPanel holdingPanel;
    private boolean isInternational;
    // Execution Context States (Phase 11)
    private SwingWorker<Void, Void> currentWorker;
    private GeneticEngine currentEngine;
    private List<Gate> systemGates;

    public AirportDashboardFrame() {
        // Frame Configuration
        setTitle("Airport Scheduling System - View Layer");
        setSize(1400, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_MAIN);

        // Build UI Components
        add(createRightSidebar(), BorderLayout.EAST);
        add(createCenterContent(), BorderLayout.CENTER);
    }

    /**
     * 2. Right Sidebar (East - RightSidebarPanel)
     */
    private JPanel createRightSidebar() {
        JPanel rightSidebar = new JPanel();
        rightSidebar.setLayout(new BoxLayout(rightSidebar, BoxLayout.Y_AXIS));
        rightSidebar.setBackground(BG_MAIN);
        rightSidebar.setPreferredSize(new Dimension(250, 0));
        rightSidebar.setBorder(new EmptyBorder(10, 10, 10, 10)); // 10px all around

        rightSidebar.add(createControlPanel());
        rightSidebar.add(Box.createRigidArea(new Dimension(0, 15))); // Gap between panels
        rightSidebar.add(createStatsPanel());
        rightSidebar.add(Box.createRigidArea(new Dimension(0, 15)));
        rightSidebar.add(createLegendPanel());

        return rightSidebar;
    }

    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new GridLayout(4, 1, 0, 10)); // 4 rows, 1 col, 10px v-gap
        controlPanel.setBackground(BG_PANEL);
        controlPanel.setBorder(createStyledTitledBorder("Controls"));

        // Buttons
        btnStartSimulation = createStyledButton("Start Simulation", BTN_BLUE, true);
        btnStartSimulation.addActionListener(e -> runSimulation());

        btnPause = createStyledButton("Pause", BTN_BASIC, false);
        btnPause.addActionListener(e -> togglePause());

        btnReset = createStyledButton("Reset", new Color(75, 85, 99), false); // #4b5563
        btnReset.addActionListener(e -> resetSimulation());

        controlPanel.add(btnStartSimulation);
        controlPanel.add(createStyledButton("Trigger Delay", BTN_RED_, false));
        controlPanel.add(btnPause);
        controlPanel.add(btnReset);

        return controlPanel;
    }

    private JPanel createLegendPanel() {
        JPanel legendPanel = new JPanel();
        legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
        legendPanel.setBackground(BG_PANEL);
        legendPanel.setBorder(createStyledTitledBorder("Aircraft & Airline Legend"));

        // Aircraft Colors
        JPanel colorsPanel = new JPanel(new GridLayout(3, 1, 0, 5));
        colorsPanel.setBackground(BG_PANEL);
        colorsPanel.add(createLegendRow("Jumbo Body", new Color(59, 130, 246))); // Blue
        colorsPanel.add(createLegendRow("Large Body", new Color(16, 185, 129))); // Green
        colorsPanel.add(createLegendRow("Small Body", new Color(234, 179, 8))); // Yellow
        colorsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Airline Codes
        JLabel airlineLabel = new JLabel("<html><b>Airlines:</b><br/>" +
                "LY - El Al<br/>" +
                "DL - Delta<br/>" +
                "LH - Lufthansa<br/>" +
                "UA - United<br/>" +
                "BA - British Airways</html>");
        airlineLabel.setForeground(TEXT_SECONDARY);
        airlineLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        airlineLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        airlineLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        legendPanel.add(colorsPanel);
        legendPanel.add(airlineLabel);

        legendPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        legendPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250));
        return legendPanel;
    }

    private JPanel createLegendRow(String text, Color color) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setBackground(BG_PANEL);

        JLabel colorBox = new JLabel();
        colorBox.setOpaque(true);
        colorBox.setBackground(color);
        colorBox.setPreferredSize(new Dimension(16, 16));

        JLabel textLabel = new JLabel(text);
        textLabel.setForeground(TEXT_SECONDARY);
        textLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        row.add(colorBox);
        row.add(textLabel);
        return row;
    }

    private JPanel createStatsPanel() {
        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBackground(BG_PANEL);
        statsPanel.setBorder(createStyledTitledBorder("Live Stats"));

        // Add Statistic Labels
        statsPanel.add(createStatRow("Total Flights", lblTotalFlights));
        statsPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        statsPanel.add(createStatRow("Assigned", lblAssigned));
        statsPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        statsPanel.add(createStatRow("Holding", lblHolding));
        statsPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        statsPanel.add(createStatRow("Fitness Score", lblFitness));
        statsPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        statsPanel.add(createStatRow("Current Generation", lblGeneration));

        statsPanel.setMaximumSize(new Dimension(250, 360));
        return statsPanel;
    }

    private JPanel createStatRow(String labelText, JLabel lblValue) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(BG_PANEL);
        row.setMaximumSize(new Dimension(230, 50));

        JLabel lblTitle = new JLabel(labelText);
        lblTitle.setForeground(TEXT_SECONDARY);
        lblTitle.setFont(new Font("SansSerif", Font.PLAIN, 13));

        lblValue.setForeground(TEXT_PRIMARY);
        lblValue.setFont(new Font("SansSerif", Font.BOLD, 26));

        row.add(lblTitle, BorderLayout.NORTH);
        row.add(lblValue, BorderLayout.SOUTH);

        return row;
    }

    /**
     * 3. Center Content Area (Center)
     */
    private JPanel createCenterContent() {
        JPanel centerContent = new JPanel(new BorderLayout(0, 10));
        centerContent.setBackground(BG_MAIN);
        centerContent.setBorder(new EmptyBorder(10, 0, 10, 0));

        // 3a. Top/Center: GanttChartPanel wrapped in JScrollPane
        ganttChartPanel = new GanttChartPanel();

        JScrollPane scrollPane = new JScrollPane(ganttChartPanel);
        scrollPane.setBorder(createStyledTitledBorder("Flight Schedule"));
        scrollPane.getViewport().setBackground(BG_PANEL);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);

        // Add the scroll pane to the center (and do NOT overwrite it later!)
        centerContent.add(scrollPane, BorderLayout.CENTER);

        // 3b. Bottom: Holding Panel wrapped in JScrollPane
        holdingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 15));
        holdingPanel.setBackground(BG_PANEL);

        JScrollPane holdingScroll = new JScrollPane(holdingPanel);
        holdingScroll.setBorder(createStyledTitledBorder("Unassigned / Holding Stack"));
        holdingScroll.setPreferredSize(new Dimension(1200, 150));
        holdingScroll.getViewport().setBackground(BG_PANEL);
        holdingScroll.getVerticalScrollBar().setUnitIncrement(16);
        holdingScroll.getHorizontalScrollBar().setUnitIncrement(16);

        // Remove dummy initialization label for a clean slate

        centerContent.add(holdingScroll, BorderLayout.SOUTH);

        return centerContent;
    }

    private void updateHoldingPanel(PriorityQueue<Flight> holdingFlights) {
        holdingPanel.removeAll();
        PriorityQueue<Flight> copy = new PriorityQueue<>(holdingFlights);
        while (!copy.isEmpty()) {
            Flight f = copy.poll();
            String arrTime = String.format("%02d:%02d", f.getArrivalTime() / 60, f.getArrivalTime() % 60);
            String depTime = String.format("%02d:%02d", f.getDepartureTime() / 60, f.getDepartureTime() % 60);
            String intStatus = f.isInternational() ? "(I)" : "(D)";
            JLabel lblHoldingFlight = new JLabel(
                    f.getFlightCode() + " " + intStatus + " (" + f.getType() + ") [" + arrTime + " - " + depTime + "]");
            lblHoldingFlight.setOpaque(true);
            lblHoldingFlight.setBackground(BTN_RED_);
            lblHoldingFlight.setForeground(TEXT_PRIMARY);
            lblHoldingFlight.setFont(new Font("SansSerif", Font.BOLD, 14));
            lblHoldingFlight.setBorder(new EmptyBorder(10, 15, 10, 15));
            holdingPanel.add(lblHoldingFlight);
        }
        holdingPanel.revalidate();
        holdingPanel.repaint();
    }

    private CompoundBorder createStyledTitledBorder(String title) {
        LineBorder lineBorder = new LineBorder(BTN_BASIC, 1, true);
        TitledBorder titledBorder = BorderFactory.createTitledBorder(lineBorder, title);
        titledBorder.setTitleColor(TEXT_SECONDARY);
        titledBorder.setTitleFont(new Font("SansSerif", Font.BOLD, 12));

        return new CompoundBorder(titledBorder, new EmptyBorder(10, 10, 10, 10));
    }

    private JButton createStyledButton(String text, Color bg, boolean bold) {
        JButton button = new JButton(text);
        button.setBackground(bg);
        button.setForeground(TEXT_PRIMARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, 14));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.setPreferredSize(new Dimension(button.getPreferredSize().width, 35));
        return button;
    }

    private boolean isGateLargeEnough(GateSize gateSize, PlaneType planeType) {
        if (planeType == PlaneType.SMALL_BODY)
            return true;
        if (planeType == PlaneType.LARGE_BODY)
            return gateSize == GateSize.SIZE_LARGE || gateSize == GateSize.SIZE_JUMBO;
        if (planeType == PlaneType.JUMBO_BODY)
            return gateSize == GateSize.SIZE_JUMBO;
        return false;
    }

    private void processAndDisplayFlights(List<Flight> currentFlights, double fitnessScore, int currentGeneration) {
        PriorityQueue<Flight> holdingFlights = new PriorityQueue<>((f1, f2) -> {
            int p1 = f1.getType() == PlaneType.JUMBO_BODY ? 3 : (f1.getType() == PlaneType.LARGE_BODY ? 2 : 1);
            int p2 = f2.getType() == PlaneType.JUMBO_BODY ? 3 : (f2.getType() == PlaneType.LARGE_BODY ? 2 : 1);
            if (p1 != p2)
                return Integer.compare(p2, p1);
            return Integer.compare(f1.getArrivalTime(), f2.getArrivalTime());
        });
        Map<Integer, List<Flight>> gateMap = new java.util.HashMap<>();

        for (Flight f : currentFlights) {
            if (f.getAssignedGate() != null) {
                if (!isGateLargeEnough(f.getAssignedGate().getSize(), f.getType()) ||
                        f.isInternational() != f.getAssignedGate().isInternational()) {
                    f.setAssignedGate(null);
                    holdingFlights.add(f);
                } else {
                    int gateId = f.getAssignedGate().getId();
                    gateMap.putIfAbsent(gateId, new ArrayList<>());
                    gateMap.get(gateId).add(f);
                }
            }
        }

        for (List<Flight> gateFlights : gateMap.values()) {
            gateFlights.sort((f1, f2) -> Integer.compare(f1.getArrivalTime(), f2.getArrivalTime()));
            int lastEndTime = -1;
            java.util.Iterator<Flight> iter = gateFlights.iterator();
            while (iter.hasNext()) {
                Flight f = iter.next();
                if (f.getArrivalTime() < lastEndTime) {
                    f.setAssignedGate(null);
                    holdingFlights.add(f);
                    iter.remove(); // CRITICAL: remove from the gateMap so it doesn't phantom-block other flights!
                } else {
                    lastEndTime = f.getDepartureTime();
                }
            }
        }

        // Phase 20: Greedy Repair Algorithm (Run only on Final Step)
        if (currentGeneration == -1 && systemGates != null) {
            PriorityQueue<Flight> stillHolding = new PriorityQueue<>(holdingFlights.comparator());
            while (!holdingFlights.isEmpty()) {
                Flight f = holdingFlights.poll();
                boolean assigned = false;

                for (Gate gate : systemGates) {
                    if (isGateLargeEnough(gate.getSize(), f.getType())
                            && gate.isInternational() == f.isInternational()) {
                        int gateId = gate.getId();
                        List<Flight> assignedToGate = gateMap.getOrDefault(gateId, new ArrayList<>());

                        boolean canFit = true;
                        for (Flight existingFlight : assignedToGate) {
                            if (!(f.getDepartureTime() <= existingFlight.getArrivalTime() ||
                                    f.getArrivalTime() >= existingFlight.getDepartureTime())) {
                                canFit = false;
                                break;
                            }
                        }

                        if (canFit) {
                            f.setAssignedGate(gate);
                            assignedToGate.add(f);
                            assignedToGate.sort(Comparator.comparingInt(Flight::getArrivalTime));
                            gateMap.put(gateId, assignedToGate);
                            assigned = true;
                            // Add to GA fitness manually for UI display consistency of 10k Base Score Match
                            fitnessScore += 10000;
                            break;
                        }
                    }
                }
                if (!assigned) {
                    stillHolding.add(f);
                }
            }
            holdingFlights = stillHolding;
        }

        int currentAssigned = currentFlights.size() - holdingFlights.size();
        int currentHolding = holdingFlights.size();

        ganttChartPanel.updateSchedule(currentFlights);
        updateHoldingPanel(holdingFlights);

        lblTotalFlights.setText(String.valueOf(currentFlights.size()));
        lblAssigned.setText(String.valueOf(currentAssigned));
        lblHolding.setText(String.valueOf(currentHolding));
        lblFitness.setText(String.format("%.0f", fitnessScore));


        if (currentGeneration != -1) {
            lblGeneration.setText(String.valueOf(currentGeneration));
        } else {
            lblGeneration.setText("Complete");
        }
    }

    private void runSimulation() {
        // Reset the Pause button state cleanly on new runs
        btnPause.setText("Pause");
        btnPause.setBackground(BTN_BASIC);

        btnStartSimulation.setText("Calculating...");
        btnStartSimulation.setEnabled(false);

        currentWorker = new SwingWorker<Void, Void>() {
            private List<Flight> finalFlights;
            private double finalFitness;

            @Override
            protected Void doInBackground() throws Exception {
                FlightRepository repo = new FlightRepository();
                List<Gate> gates = new ArrayList<>();
                systemGates = gates;
                TerminalGraph graph = new TerminalGraph();

                int gateIdCounter = 1;
                // Gates 1-10: SMALL, Domestic
                for (int i = 0; i < 10; i++) {
                    Gate g = new Gate(gateIdCounter++, GateSize.SIZE_SMALL, i * 10, 0, false);
                    gates.add(g);
                    graph.addGate(g);
                }
                // Gates 11-15: SMALL, International
                for (int i = 0; i < 5; i++) {
                    Gate g = new Gate(gateIdCounter++, GateSize.SIZE_SMALL, 100 + i * 10, 0, true);
                    gates.add(g);
                    graph.addGate(g);
                }

                // Gates 16-20: LARGE, Domestic
                for (int i = 0; i < 5; i++) {
                    Gate g = new Gate(gateIdCounter++, GateSize.SIZE_LARGE, 150 + i * 15, 0, false);
                    gates.add(g);
                    graph.addGate(g);
                }
                // Gates 21-24: LARGE, International
                for (int i = 0; i < 4; i++) {
                    Gate g = new Gate(gateIdCounter++, GateSize.SIZE_LARGE, 225 + i * 15, 0, true);
                    gates.add(g);
                    graph.addGate(g);
                }

                // Gates 25-30: JUMBO, International (All Jumbo are international now)
                for (int i = 0; i < 6; i++) {
                    Gate g = new Gate(gateIdCounter++, GateSize.SIZE_JUMBO, 300 + i * 20, 0, true);
                    gates.add(g);
                    graph.addGate(g);
                }

                for (int i = 0; i < gates.size() - 1; i++) {
                    graph.connectGates(gates.get(i), gates.get(i + 1));
                }

                graph.initializeDistanceMatrix();

                // Generate a fresh set of flights to the CSV file
                model.utils.FlightCSVGenerator.main(new String[0]);

                // Load flights from the generated CSV file directly into the repository
                model.utils.CSVLoader.loadFlights("flights.csv", repo);

                currentEngine = new GeneticEngine(repo, gates, graph);
                currentEngine.setParameters(100, 0.05, 500); // Massive constraints limit for fast UI response
                int[] bestSolution = currentEngine.run((progressFlights, currentFitness, generation) -> {
                    SwingUtilities
                            .invokeLater(() -> processAndDisplayFlights(progressFlights, currentFitness, generation));
                });

                FitnessEvaluator evaluator = new FitnessEvaluator(graph, repo, gates);
                finalFlights = new ArrayList<>(repo.getAllFlights());
                finalFitness = evaluator.calculateFitness(bestSolution, finalFlights);

                // Map GA Array directly to the Flight Models
                for (int i = 0; i < finalFlights.size(); i++) {
                    int gateId = bestSolution[i];
                    Gate assigned = gates.get(gateId - 1);
                    Flight f = finalFlights.get(i);
                    f.setAssignedGate(assigned);
                }

                return null;
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled())
                        return;
                    get(); // Wait and catch potential worker errors
                    processAndDisplayFlights(finalFlights, finalFitness, -1);
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(AirportDashboardFrame.this, "Simulation error: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnStartSimulation.setText("Start Simulation");
                    btnStartSimulation.setEnabled(true);
                }
            }
        };

        currentWorker.execute();
    }

    private void togglePause() {
        if (currentEngine == null)
            return;

        if (currentEngine.isPaused()) {
            currentEngine.resumeEngine();
            btnPause.setText("Pause");
            btnPause.setBackground(BTN_BASIC);
        } else {
            currentEngine.pauseEngine();
            btnPause.setText("Resume");
            btnPause.setBackground(new Color(16, 185, 129)); // #10b981 Emerald Green
        }
    }

    private void resetSimulation() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }
        if (currentEngine != null) {
            currentEngine.resumeEngine(); // Unblock interrupted threads
        }

        ganttChartPanel.updateSchedule(new ArrayList<>());
        updateHoldingPanel(new PriorityQueue<>());
        lblTotalFlights.setText("0");
        lblAssigned.setText("0");
        lblHolding.setText("0");
        lblFitness.setText("0");
        lblGeneration.setText("0");

        btnStartSimulation.setText("Start Simulation");
        btnStartSimulation.setEnabled(true);

        btnPause.setText("Pause");
        btnPause.setBackground(BTN_BASIC);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            AirportDashboardFrame frame = new AirportDashboardFrame();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}