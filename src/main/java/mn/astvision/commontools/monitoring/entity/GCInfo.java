package mn.astvision.commontools.monitoring.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GCInfo {
    private float usedBeforeMb;
    private float usedAfterMb;
    private float freedMb;
    private boolean success;
}
