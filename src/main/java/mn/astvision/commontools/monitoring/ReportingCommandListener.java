package mn.astvision.commontools.monitoring;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import lombok.extern.slf4j.Slf4j;
import mn.astvision.commontools.monitoring.sysreport.QueryExecutionContext;
import mn.astvision.commontools.monitoring.sysreport.QueryReport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ReportingCommandListener implements CommandListener {
    private static final ThreadLocal<QueryExecutionContext> CONTEXT = new ThreadLocal<>();
    private final Map<Integer, QueryReport> runningReports = new ConcurrentHashMap<>();
    private final Queue<QueryReport> reports = new ConcurrentLinkedQueue<>();
    private volatile boolean enabled = false;

    public static void setRequestContext(QueryExecutionContext context) {
        CONTEXT.set(context);
    }

    public static void clearRequestContext() {
        CONTEXT.remove();
    }

//    @Override
//    public void setApplicationContext(ApplicationContext applicationContext) {
//        mongoTemplate = applicationContext.getBean(MongoTemplate.class);
//    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public void clear() {
        reports.clear();
    }

    public List<QueryReport> getReports() {
        return new ArrayList<>(reports);
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        if (!enabled) return;

        QueryExecutionContext ctx = CONTEXT.get();

        QueryReport report = new QueryReport();
        report.setCommandName(event.getCommandName());
        report.setDatabase(event.getDatabaseName());
        report.setCommandJson(event.getCommand().toJson());

        report.setStartTimeMs(System.currentTimeMillis());
        report.setEndTime(LocalDateTime.now());


        if (ctx != null) {
            report.setContext(ctx);
        }

        reports.add(report);
        runningReports.put(event.getRequestId(), report); // store for later
    }


    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        if (!enabled) return;

        QueryReport report = runningReports.remove(event.getRequestId());
        if (report != null) {
            long endNs = System.nanoTime();
            report.setDurationNs(event.getElapsedTime(TimeUnit.NANOSECONDS));
            report.setDurationMs(report.getDurationNs() / 1_000_000);
            report.setEndTime(LocalDateTime.now());
            report.setEndTimeMs(endNs / 1_000_000);

            // optionally run explain for reads
            if ("find".equals(report.getCommandName()) || "aggregate".equals(report.getCommandName())) {
//                runExplain(report);
            }
        }
    }


    @Override
    public void commandFailed(CommandFailedEvent event) {
        if (!enabled) return;
        QueryExecutionContext ctx = CONTEXT.get();
        QueryReport report = new QueryReport();
        report.setCommandName(event.getCommandName());
        report.setError(event.getThrowable().getMessage());
        report.setStartTimeMs(System.nanoTime());
        report.setStartTime(LocalDateTime.now());
        if (ctx != null) {
            report.setContext(ctx);
        }
        reports.add(report);
    }
}
