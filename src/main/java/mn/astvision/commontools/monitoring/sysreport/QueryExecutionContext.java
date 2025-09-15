package mn.astvision.commontools.monitoring.sysreport;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class QueryExecutionContext {
    private final String serviceName;       // Controller/service name
    private final String endpoint;          // Method + HTTP path
    private final String userId;            // Authenticated user ID
    private final String userRole;          // Primary role or comma-separated roles
    private final Integer pageNumber;       // If paginated request
    private final Integer pageSize;         // If paginated request
    private final String requestId;         // Optional request ID / trace ID
}
