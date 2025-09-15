package mn.astvision.commontools.monitoring.entity;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PerformanceSummary {
    private String label;
    private int runs;
    private long avgTime;       // in ms
    private long bestTime;      // in ms
    private long worstTime;     // in ms
    private long avgMemoryDiff; // in KB

    @Override
    public String toString() {
        //@formatter:off
        return String.join("\n", List.of(
                "=== Perf Summary: " + label + " ===",
                "Runs: " + runs,
                "Avg Time: " + avgTime + " ms",
                "Best Time: " + bestTime + " ms",
                "Worst Time: " + worstTime + " ms",
                "Avg Memory Diff: " + avgMemoryDiff + " KB"
        ));
        //@formatter:on
    }

    public void print() {
        System.out.println(this);
    }
}
