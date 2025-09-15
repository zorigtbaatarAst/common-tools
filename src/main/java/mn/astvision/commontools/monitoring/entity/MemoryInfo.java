package mn.astvision.commontools.monitoring.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MemoryInfo {
    private float used;
    private float free;
    private float total;
    private float max;
    private double usedPercent;
}
