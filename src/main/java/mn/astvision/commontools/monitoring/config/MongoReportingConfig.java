package mn.astvision.commontools.monitoring.config;

import mn.astvision.commontools.monitoring.ReportingCommandListener;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoReportingConfig {

    private final ReportingCommandListener reportingCommandListener;

    public MongoReportingConfig(ReportingCommandListener reportingCommandListener) {
        this.reportingCommandListener = reportingCommandListener;
    }

    @Bean
    public MongoClientSettingsBuilderCustomizer attachCommandListener() {
        return builder -> builder.addCommandListener(reportingCommandListener);
    }
}
