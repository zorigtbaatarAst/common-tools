package mn.astvision.commontools.authutil;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RateLimitEntry {
    private int requestCount;
    private LocalDateTime windowStart;

    public RateLimitEntry() {
        this.requestCount = 0;
        this.windowStart = LocalDateTime.now();
    }

    public int incrementAndGet() {
        requestCount++;
        return requestCount;
    }

    public void reset() {
        requestCount = 1;
        windowStart = LocalDateTime.now();
    }

}
