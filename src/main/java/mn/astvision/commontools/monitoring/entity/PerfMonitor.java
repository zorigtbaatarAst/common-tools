package mn.astvision.commontools.monitoring.entity;

import java.util.ArrayList;
import java.util.List;

public class PerfMonitor {
    private final List<Long> times = new ArrayList<>();
    private final List<Long> memories = new ArrayList<>();

    private long startTime;
    private long startMemory;

    public static PerfMonitor create() {
        return new PerfMonitor();
    }

    public void start() {
        startTime = System.nanoTime();
        startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    public void stop(String label) {
        long endTime = System.nanoTime();
        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long elapsedTime = (endTime - startTime) / 1_000_000; // ms
        long memoryDiff = Math.abs(endMemory - startMemory) / 1024;

        times.add(elapsedTime);
        memories.add(memoryDiff);

        System.out.printf("[%s] Run %d -> Time: %d ms | Memory diff: %d KB%n",
                label, times.size(), elapsedTime, memoryDiff);
    }

    public long avgTime() {
        return times.stream().mapToLong(Long::longValue).sum() / Math.max(1, times.size());
    }

    public long avgMemory() {
        return memories.stream().mapToLong(Long::longValue).sum() / Math.max(1, memories.size());
    }

    public long bestTime() {
        return times.stream().mapToLong(Long::longValue).min().orElse(0);
    }

    public long worstTime() {
        return times.stream().mapToLong(Long::longValue).max().orElse(0);
    }

    public void compare(PerfMonitor other, String label) {
        System.out.printf("[%s] Comparison -> Time: this(avg %d ms, best %d ms) vs other(avg %d ms, best %d ms) | "
                        + "Memory: this(avg %d KB) vs other(avg %d KB)%n",
                label,
                this.avgTime(), this.bestTime(),
                other.avgTime(), other.bestTime(),
                this.avgMemory(), other.avgMemory()
        );
    }

    public PerformanceSummary summary(String label) {
        return PerformanceSummary.builder()
                .label(label)
                .runs(times.size())
                .avgTime(avgTime())
                .bestTime(bestTime())
                .worstTime(worstTime())
                .avgMemoryDiff(avgMemory())
                .build();
    }

    public PerformanceSummary summary() {
        return summary("");
    }

}
