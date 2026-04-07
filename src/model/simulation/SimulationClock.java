package model.simulation;

import javax.swing.Timer;

/**
 * Drives the simulation forward by firing time ticks at a fixed interval.
 * Each tick represents one simulated minute.
 * Uses javax.swing.Timer so all callbacks fire on the EDT — no synchronization needed.
 */
public class SimulationClock {

    private static final int TICK_INTERVAL_MS = 100; // real ms per simulated minute
    private static final int MINUTES_PER_TICK = 1;   // simulated minutes per tick

    private int currentTime;
    private final Timer timer;
    private Runnable tickListener;

    /** @param onTick called with the new currentTime on every tick (runs on EDT) */
    public SimulationClock(int startTime, java.util.function.IntConsumer onTick) {
        this.currentTime = startTime;
        this.timer = new Timer(TICK_INTERVAL_MS, e -> {
            currentTime += MINUTES_PER_TICK;
            onTick.accept(currentTime);
            if (tickListener != null) {
                tickListener.run();
            }
        });
        this.timer.setCoalesce(true);
    }

    public void setTickListener(Runnable listener) {
        this.tickListener = listener;
    }

    public void start() {
        timer.start();
    }

    public void pause() {
        timer.stop();
    }

    public void resume() {
        timer.start();
    }

    public boolean isRunning() {
        return timer.isRunning();
    }

    public void reset(int startTime) {
        timer.stop();
        this.currentTime = startTime;
    }

    public int getCurrentTime() {
        return currentTime;
    }
}
