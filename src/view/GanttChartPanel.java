package view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import model.Flight;
import model.enums.PlaneType;

public class GanttChartPanel extends JPanel {

    // Theme Colors
    private static final Color BG_PANEL = new Color(42, 46, 57); // #2a2e39
    private static final Color GRID_LINE = new Color(55, 65, 81); // #374151
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255); // #ffffff
    private static final Color TEXT_SECONDARY = new Color(160, 165, 177);// #a0a5b1

    // Flight Block Colors
    private static final Color EMERALD = new Color(16, 185, 129); // #10b981
    private static final Color INDIGO = new Color(99, 102, 241); // #6366f1
    private static final Color AMBER = new Color(245, 158, 11); // #f59e0b

    // Chart Configuration
    private static final int NUM_GATES = 30;
    private static final int ROW_HEIGHT = 45;
    private static final int START_HOUR = 6; // 06:00
    private static final int END_HOUR = 24; // 24:00 (Midnight)
    private static final int TOTAL_MINUTES = (END_HOUR - START_HOUR) * 60;

    // Margins constraints for drawing area
    private static final int MARGIN_LEFT = 110; // Widened for the "[JUMBO]" labels
    private static final int MARGIN_RIGHT = 30;
    private static final int MARGIN_TOP = 40;
    private static final int MARGIN_BOTTOM = 20;

    private List<FlightBlock> flightBlocks;

    public GanttChartPanel() {
        setBackground(BG_PANEL);
        int calculatedHeight = (NUM_GATES * ROW_HEIGHT) + MARGIN_TOP + MARGIN_BOTTOM;
        setPreferredSize(new Dimension(1200, calculatedHeight));
        flightBlocks = new ArrayList<>();
    }

    public void updateSchedule(List<Flight> realFlights) {
        flightBlocks.clear();
        for (Flight f : realFlights) {
            if (f.getAssignedGate() != null) {
                Color c = EMERALD;
                if (f.getType() == PlaneType.LARGE_BODY)
                    c = INDIGO;
                else if (f.getType() == PlaneType.JUMBO_BODY)
                    c = AMBER;

                // Map the Gate ID to 0-based array index
                int gateRow = f.getAssignedGate().getId() - 1;

                flightBlocks.add(new FlightBlock(
                        f.getFlightCode(),
                        f.getType().toString(),
                        gateRow,
                        f.getArrivalTime(),
                        f.getDepartureTime(),
                        c));
            }
        }

        // Sort the flights by Gate, then by Start Time
        flightBlocks.sort((b1, b2) -> {
            if (b1.gateRow != b2.gateRow) {
                return Integer.compare(b1.gateRow, b2.gateRow);
            }
            return Integer.compare(b1.startMinute, b2.startMinute);
        });

        // Detect collisions
        int[] lastEndMinute = new int[NUM_GATES];
        for (int i = 0; i < NUM_GATES; i++)
            lastEndMinute[i] = -1;

        for (FlightBlock block : flightBlocks) {
            if (block.startMinute < lastEndMinute[block.gateRow]) {
                block.isCollision = true;
            }
            // Update the last end minute if this flight ends later
            if (block.endMinute > lastEndMinute[block.gateRow]) {
                lastEndMinute[block.gateRow] = block.endMinute;
            }
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Setup Antialiasing for smooth graphics
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        // In our fixed-row setup, we don't recalculate rowHeight based on panel height
        // Calculate drawable area width and pixel scale for the X axis
        int drawWidth = width - MARGIN_LEFT - MARGIN_RIGHT;
        double pixelsPerMinute = (double) drawWidth / TOTAL_MINUTES;

        drawGridAndAxes(g2d, drawWidth, height, ROW_HEIGHT, pixelsPerMinute);
        drawFlights(g2d, ROW_HEIGHT, pixelsPerMinute);
    }

    private void drawGridAndAxes(Graphics2D g2d, int drawWidth, int panelHeight, double rowHeight,
            double pixelsPerMinute) {
        // 1. Draw Y-axis (Gates)
        Font fontGate = new Font("SansSerif", Font.BOLD, 12);
        Font fontSize = new Font("SansSerif", Font.PLAIN, 10);

        for (int i = 0; i < NUM_GATES; i++) {
            int yPos = MARGIN_TOP + (int) (i * rowHeight);
            int gateNum = i + 1;

            // Determine sizing string
            String sizeCategory;
            if (gateNum <= 15)
                sizeCategory = "[SMALL]";
            else if (gateNum <= 25)
                sizeCategory = "[LARGE]";
            else
                sizeCategory = "[JUMBO]";

            String gateLabel = "GATE " + gateNum;

            // Draw Gate Identifier
            g2d.setFont(fontGate);
            g2d.setColor(TEXT_PRIMARY); // #ffffff
            FontMetrics fmGate = g2d.getFontMetrics();
            int gateLabelY = yPos + (int) (rowHeight / 2) + (fmGate.getAscent() / 2) - 2;
            int gateStrWidth = fmGate.stringWidth(gateLabel);

            // Draw Size Category
            g2d.setFont(fontSize);
            g2d.setColor(new Color(156, 163, 175)); // #9ca3af (Gray)
            FontMetrics fmSize = g2d.getFontMetrics();
            int sizeStrWidth = fmSize.stringWidth(sizeCategory);

            // Align them right-justified looking clean
            int totalLabelWidth = gateStrWidth + 5 + sizeStrWidth;
            int startX = MARGIN_LEFT - totalLabelWidth - 10;

            g2d.setFont(fontGate);
            g2d.setColor(TEXT_PRIMARY);
            g2d.drawString(gateLabel, startX, gateLabelY);

            g2d.setFont(fontSize);
            g2d.setColor(new Color(156, 163, 175));
            g2d.drawString(sizeCategory, startX + gateStrWidth + 5, gateLabelY);

            // Draw Horizontal Grid Line (separator)
            g2d.setColor(GRID_LINE);
            g2d.drawLine(MARGIN_LEFT, yPos, MARGIN_LEFT + drawWidth, yPos);
        }

        // Draw bottom boundary line for the last gate
        g2d.drawLine(MARGIN_LEFT, MARGIN_TOP + (int) (NUM_GATES * rowHeight), MARGIN_LEFT + drawWidth,
                MARGIN_TOP + (int) (NUM_GATES * rowHeight));

        // 2. Draw X-axis (Timeline / Hours)
        int numHours = END_HOUR - START_HOUR;
        for (int i = 0; i <= numHours; i++) {
            int currentHour = START_HOUR + i;
            int xPos = MARGIN_LEFT + (int) (i * 60 * pixelsPerMinute);

            // Draw Vertical Hour Line
            g2d.setColor(GRID_LINE);
            g2d.drawLine(xPos, MARGIN_TOP, xPos, panelHeight - MARGIN_BOTTOM);

            // Draw Hour Label
            g2d.setColor(TEXT_SECONDARY);
            String timeLabel = String.format("%02d:00", currentHour);
            FontMetrics fm = g2d.getFontMetrics();
            int labelWidth = fm.stringWidth(timeLabel);
            g2d.drawString(timeLabel, xPos - (labelWidth / 2), MARGIN_TOP - 10);
        }
    }

    private void drawFlights(Graphics2D g2d, double rowHeight, double pixelsPerMinute) {
        g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g2d.getFontMetrics();

        int blockPaddingY = 4; // Padding top and bottom within the row

        for (FlightBlock flight : flightBlocks) {
            // Ignore flights outside our drawn timeline window completely
            if (flight.endMinute < (START_HOUR * 60) || flight.startMinute > (END_HOUR * 60)) {
                continue;
            }

            // Map and limit drawing bounds so blocks don't hang off the edge
            int drawStartMin = Math.max(flight.startMinute, START_HOUR * 60);
            int drawEndMin = Math.min(flight.endMinute, END_HOUR * 60);

            // Calculate exact position mapped to minutes
            int xStart = MARGIN_LEFT + (int) ((drawStartMin - (START_HOUR * 60)) * pixelsPerMinute);
            int xEnd = MARGIN_LEFT + (int) ((drawEndMin - (START_HOUR * 60)) * pixelsPerMinute);
            int blockWidth = Math.max(xEnd - xStart, 5); // Ensure tiny flights are still slightly visible

            int yPos = MARGIN_TOP + (int) (flight.gateRow * rowHeight) + blockPaddingY;
            int blockHeight = (int) rowHeight - (blockPaddingY * 2);

            // Draw Rounded Rectangle Block (Color changes if it's a collision overlap)
            Color blockColor = flight.isCollision ? new Color(239, 68, 68) : flight.color; // #ef4444 Red
            g2d.setColor(blockColor);
            RoundRectangle2D rect = new RoundRectangle2D.Float(xStart, yPos, blockWidth, blockHeight, 10, 10);
            g2d.fill(rect);

            // Draw thick Collision Border
            if (flight.isCollision) {
                g2d.setColor(Color.YELLOW);
                Stroke oldStroke = g2d.getStroke();
                g2d.setStroke(new BasicStroke(2.0f));
                g2d.draw(rect);
                g2d.setStroke(oldStroke);
            }

            // Draw Flight Text
            g2d.setColor(TEXT_PRIMARY);
            String label = flight.flightCode + " (" + flight.planeType + ")";

            // Only draw text if the block is wide enough to reasonably hold it
            int textWidth = fm.stringWidth(label);
            if (textWidth + 10 < blockWidth) {
                int textX = xStart + (blockWidth - textWidth) / 2;
                int textY = yPos + (blockHeight / 2) + (fm.getAscent() / 2) - 2;
                g2d.drawString(label, textX, textY);
            } else {
                // Determine if code alone fits
                int shortTextWidth = fm.stringWidth(flight.flightCode);
                if (shortTextWidth + 4 < blockWidth) {
                    int textX = xStart + (blockWidth - shortTextWidth) / 2;
                    int textY = yPos + (blockHeight / 2) + (fm.getAscent() / 2) - 2;
                    g2d.drawString(flight.flightCode, textX, textY);
                }
            }
        }
    }

    // --- Internal Render Data Class ---
    private static class FlightBlock {
        String flightCode;
        String planeType;
        int gateRow; // 0 to 29 representing Gate 1 to Gate 30
        int startMinute; // Absolute minutes from midnight
        int endMinute;
        Color color;
        boolean isCollision = false;

        public FlightBlock(String flightCode, String planeType, int gateRow, int startMinute, int endMinute,
                Color color) {
            this.flightCode = flightCode;
            this.planeType = planeType;
            this.gateRow = gateRow;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.color = color;
        }
    }
}
