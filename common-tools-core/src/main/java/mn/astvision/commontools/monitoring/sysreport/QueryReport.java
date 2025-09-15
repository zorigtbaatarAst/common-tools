package mn.astvision.commontools.monitoring.sysreport;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QueryReport {

    // ------------------------ Mongo Command Info ------------------------
    private String commandName;
    private String database;
    private String commandJson;
    private long startTimeMs;
    private long endTimeMs;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMs;

    /**
     * Duration in nanoseconds for high-precision timing
     */
    private long durationNs;

    /**
     * Error message if the command failed
     */
    private String error;

    // ------------------------ Mongo Explain / Query Info ------------------------
    private String indexUsed;
    private long keysExamined;
    private long docsExamined;

    // ------------------------ Request / Context Info ------------------------
    /**
     * Context of the HTTP request or service call
     */
    private QueryExecutionContext context;

}
