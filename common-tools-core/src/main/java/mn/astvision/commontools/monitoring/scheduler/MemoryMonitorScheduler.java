package mn.astvision.commontools.monitoring.scheduler;

import lombok.extern.slf4j.Slf4j;
import mn.astvision.commontools.monitoring.MemoryUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author zorigtbaatar
 */

@Slf4j
public class MemoryMonitorScheduler {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Starts a periodic memory monitor that warns if memory usage is above a given threshold.
     *
     * @param thresholdPercent Memory usage threshold in percent (0-100)
     * @param intervalSeconds  Interval between checks in seconds
     */
    public static void startMemoryMonitor(double thresholdPercent, long intervalSeconds) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                MemoryUtils.warnIfMemoryHigh("Periodic Memory Check", thresholdPercent);

                if (MemoryUtils.getUsedMemoryPercent() > thresholdPercent) {
                    MemoryUtils.dumpHeapThrottled("heapDump_" + System.currentTimeMillis() + ".hprof");
                }

            } catch (Exception ex) {
                System.err.println("Memory monitor failed: " + ex.getMessage());
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Stops the scheduler gracefully.
     */
    public static void stopMemoryMonitor() {
        scheduler.shutdown();
    }
}
