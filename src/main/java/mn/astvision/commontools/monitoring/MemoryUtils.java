package mn.astvision.commontools.monitoring;

import com.sun.management.HotSpotDiagnosticMXBean;
import mn.astvision.commontools.monitoring.entity.GCInfo;
import mn.astvision.commontools.monitoring.entity.MemoryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class MemoryUtils {
    public static final String DUMP_DIR = "dumpfiles";
    private static final Logger log = LoggerFactory.getLogger(MemoryUtils.class);
    private static final long DUMP_THROTTLE_MS = 10 * 60 * 1000; // 10 minutes
    private static HotSpotDiagnosticMXBean hotspotMBean;
    private static long lastDumpTime = 0;

    static {
        try {
            hotspotMBean = ManagementFactory.newPlatformMXBeanProxy(ManagementFactory.getPlatformMBeanServer(), "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize HotSpotDiagnosticMXBean", e);
        }
    }

    private MemoryUtils() {
    }

    /**
     * Dumps the current heap to the specified file.
     *
     * @param filePath path to dump heap, e.g. "heapDump.hprof"
     * @param liveOnly if true, only live objects are included
     */
    public static void dumpHeap(String filePath, boolean liveOnly) {
        try {
            hotspotMBean.dumpHeap(filePath, liveOnly);
            System.out.println("Heap dump created at: " + filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create heap dump", e);
        }
    }

    public static void dumpHeapThrottled(String fileName) {
        long now = System.currentTimeMillis();
        if (now - lastDumpTime < DUMP_THROTTLE_MS) return; // throttle

        lastDumpTime = now;

        try {
            File dir = new File(DUMP_DIR);
            if (!dir.exists()) dir.mkdirs();

            File dumpFile = new File(dir, fileName);
            dumpHeap(dumpFile.getAbsolutePath(), true);
            log.info("Heap dump written to {}", dumpFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to create heap dump", e);
        }
    }

    public static File getLastHeapDump() {
        File dir = new File(DUMP_DIR);
        if (!dir.exists() || !dir.isDirectory()) return null;

        Optional<File> lastFile = Arrays.stream(Objects.requireNonNull(dir.listFiles((d, name) -> name.endsWith(".hprof")))).max(Comparator.comparingLong(File::lastModified));
        return lastFile.orElse(null);
    }

    /**
     * Convenience method with liveOnly = true
     */
    public static void dumpHeap(String filePath) {
        dumpHeap(filePath, true);
    }

    public static void monitorMemory(String stage, Runnable task, double warningThresholdPercent) {
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();
        task.run();
        long after = runtime.totalMemory() - runtime.freeMemory();
        logMemoryWarning(stage, before, after, warningThresholdPercent);
    }

    public static void monitorPerformance(String stage, Runnable task, double warningThresholdPercent) {
        Runtime runtime = Runtime.getRuntime();
        long beforeMem = runtime.totalMemory() - runtime.freeMemory();
        long start = System.nanoTime();
        task.run();
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        long afterMem = getUsedMemory(runtime);

        logPerformance(stage, beforeMem, afterMem, durationMs, warningThresholdPercent);
    }

    public static long monitorMemoryAndGetDelta(String stage, Runnable task, double thresholdPercent) {
        Runtime runtime = Runtime.getRuntime();
        long before = getUsedMemory(runtime);
        task.run();
        long after = getUsedMemory(runtime);
        logMemoryWarning(stage, before, after, thresholdPercent);
        return after - before;
    }

    public static void monitorMemory(String stage, Runnable task) {
        monitorMemory(stage, task, 80);
    }

    /**
     * Logs warning if memory usage is dangerous
     *
     * @param stage            stage name
     * @param before           memory before in bytes
     * @param after            memory after in bytes
     * @param thresholdPercent threshold for total usage percentage of max memory
     */
    public static void logMemoryWarning(String stage, long before, long after, double thresholdPercent) {
        long diff = after - before; // bytes
        // Short-circuit if the delta is very small (<1 MB)
        if (Math.abs(diff) < 1024 * 1024) return;

        long maxMemory = Runtime.getRuntime().maxMemory();

        double diffPercent = (double) diff / maxMemory * 100;
        double usedPercent = (double) after / maxMemory * 100;

        if (usedPercent <= thresholdPercent) return;

        log.warn("""
                               {} high memory usage!
                               Used before: {} MB, after: {} MB,
                               delta: {} MB ({}%),
                               max: {} MB,
                               used%: {}
                        """,
                //@formatter:off
                    stage,
                    before / (1024 * 1024), after / (1024 * 1024),
                    diff / (1024 * 1024),
                    String.format("%.2f", diffPercent),
                    maxMemory / (1024 * 1024),
                    String.format("%.2f", usedPercent));
            //@formatter:on
    }

    // -------------------
    // MEMORY HELPERS
    // -------------------

    public static GCInfo forceGC() {
        long before = MemoryUtils.getUsedMemory();
        System.gc();
        long after = MemoryUtils.getUsedMemory();
        long freed = before - after;

        //@formatter:off
        return GCInfo.builder()
                .usedBeforeMb(toMB(before))
                .usedAfterMb(toMB(after))
                .freedMb(toMB(freed))
                .success(freed >= 0)
                .build();
        //@formatter:on

    }

    /**
     * Returns currently used memory in bytes
     */
    public static long getUsedMemory() {
        return getUsedMemory(Runtime.getRuntime());
    }

    private static long getUsedMemory(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Returns total memory available to JVM in bytes
     */
    public static long getTotalMemory() {
        return Runtime.getRuntime().totalMemory();
    }

    /**
     * Returns free memory in JVM in bytes
     */
    public static long getFreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    /**
     * Returns max memory JVM can use in bytes
     */
    public static long getMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    /**
     * Returns memory usage as a percentage of max memory
     */
    public static double getUsedMemoryPercent() {
        return (double) getUsedMemory() / getMaxMemory() * 100;
    }

    // -------------------
    // MEMORY ALERTS
    // -------------------

    /**
     * Warns if used memory exceeds threshold percent
     */
    public static void warnIfMemoryHigh(String stage, double thresholdPercent) {
        double usedPercent = getUsedMemoryPercent();
        if (usedPercent > thresholdPercent) {
            log.warn("{} memory usage high: {}%", stage, String.format("%.2f", usedPercent));

            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            //@formatter:off
            String trace = Arrays.stream(stack)
                    .skip(2) // skip getStackTrace() and warnIfMemoryHigh
                    .filter(e -> !e.getMethodName().equals("warnIfMemoryHigh")) // skip itself
                    .limit(5) // limit for readability
                    .map(new java.util.function.Function<StackTraceElement, String>() {
                        int index = 1;
                        @Override
                        public String apply(StackTraceElement e) {
                            return String.format("%d. %s.%s(%s:%d)", index++, e.getClassName(), e.getMethodName(), e.getFileName(), e.getLineNumber());
                        }
                    })
                    .collect(Collectors.joining("\n"));            //@formatter:on

            log.warn("Memory warning location:\n{}", trace);
        }
    }

    /**
     * Suggest GC and warn if memory usage still high
     */
    public static void forceGCAndWarn(String stage, double thresholdPercent) {
        System.gc();
        warnIfMemoryHigh(stage + " after GC", thresholdPercent);
    }

    // -------------------
    // MEMORY SNAPSHOTS
    // -------------------

    /**
     * Simple memory snapshot for debugging
     */
    public static MemoryInfo snapshot() {
        long used = getUsedMemory();
        long free = getFreeMemory();
        long total = getTotalMemory();
        long max = getMaxMemory();
        double usedPercent = getUsedMemoryPercent();

        return MemoryInfo.builder().used(toMB(used)).free(toMB(free)).total(toMB(total)).max(toMB(max)).usedPercent(usedPercent).build();
    }

    private static float toMB(long bytes) {
        if (bytes < 0) {
            log.warn("Memory bytes is negative: {}", bytes);
            return 0f;
        }
        return bytes / (1024f * 1024f);
    }

    public static String snapshotPretty() {
        MemoryInfo info = snapshot();
        return String.format("""
                Memory Usage:
                  Used:  %6.2f MB
                  Free:  %6.2f MB
                  Total: %6.2f MB
                  Max:   %6.2f MB
                  Used%%: %5.2f%%
                """, info.getUsed(), info.getFree(), info.getTotal(), info.getMax(), info.getUsedPercent());
    }

    /**
     * Log memory snapshot
     */
    public static void logSnapshot(String stage) {
        log.info("{} memory snapshot: {}", stage, snapshotPretty());
    }

    private static void logPerformance(String stage, long before, long after, long durationMs, double thresholdPercent) {
        if (thresholdPercent == 0) {
            return;
        }

        long diff = after - before; // bytes
        long maxMemory = Runtime.getRuntime().maxMemory();

        double usedPercent = (double) after / maxMemory * 100;
        double diffPercent = (double) diff / maxMemory * 100;

        log.info("""
                [{}] Performance Report:
                  Duration: {} ms
                  Memory before: {} MB
                  Memory after : {} MB
                  Delta        : {} MB ({}%)
                  Used percent : {}%
                  Max memory   : {} MB
                """, stage, durationMs, before / (1024 * 1024), after / (1024 * 1024), diff / (1024 * 1024), String.format("%.2f", diffPercent), String.format("%.2f", usedPercent), maxMemory / (1024 * 1024));

        if (usedPercent > thresholdPercent) {
            log.warn("[{}] Memory usage exceeded threshold ({}%) -> {}%", stage, thresholdPercent, usedPercent);
        }
    }
}
