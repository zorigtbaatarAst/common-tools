package mn.astvision.commontools.monitoring.component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mn.astvision.commontools.monitoring.ReportingCommandListener;
import mn.astvision.commontools.monitoring.sysreport.QueryExecutionContext;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ReportingContextInterceptor implements HandlerInterceptor {
    public ReportingContextInterceptor(ReportingCommandListener reportingCommandListener) {
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        QueryExecutionContext.QueryExecutionContextBuilder builder = QueryExecutionContext.builder();

        // 1️⃣ Extract service and endpoint from handler
        if (handler instanceof org.springframework.web.method.HandlerMethod handlerMethod) {
            builder.serviceName(handlerMethod.getBeanType().getSimpleName());
            builder.endpoint(handlerMethod.getMethod().getName() + " [" + request.getMethod() + " " + request.getRequestURI() + "]");
        } else {
            builder.serviceName(handler.getClass().getSimpleName());
            builder.endpoint(request.getMethod() + " " + request.getRequestURI());
        }

        // 2️⃣ Extract user info from Spring Security (if available)
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                builder.userId(auth.getName());
                // get first role or comma-separated roles

//                String roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).reduce((r1, r2) -> r1 + "," + r2).orElse(null);
//                builder.userRole(roles);
            }
        } catch (Exception ignored) {
            // No security context available
        }

        // 3️⃣ Extract pagination info (if query parameters exist)
        try {
            String pageStr = request.getParameter("page");
            String sizeStr = request.getParameter("size");
            if (pageStr != null) builder.pageNumber(Integer.parseInt(pageStr));
            if (sizeStr != null) builder.pageSize(Integer.parseInt(sizeStr));
        } catch (NumberFormatException ignored) {
            // ignore invalid numbers
        }

        // 4️⃣ Optional request/trace ID
        String requestId = request.getHeader("X-Request-ID");
        if (requestId != null) builder.requestId(requestId);

        // 5️⃣ Build and set the context
        ReportingCommandListener.setRequestContext(builder.build());

        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        ReportingCommandListener.clearRequestContext();
    }
}
