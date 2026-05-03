package com.shoptopus.core.logging;

import com.shoptopus.core.adminui.AdminStoreContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoptopus.core.logging.repository.EventLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;

@Aspect
@Component
public class ApiLoggingAspect {

    private final EventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;
    private final AdminStoreContext adminStoreContext;

    public ApiLoggingAspect(EventLogRepository eventLogRepository, ObjectMapper objectMapper, AdminStoreContext adminStoreContext) {
        this.eventLogRepository = eventLogRepository;
        this.objectMapper = objectMapper;
        this.adminStoreContext = adminStoreContext;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable failure = null;
        Object result = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            persistEvent(pjp, System.currentTimeMillis() - start, failure);
        }
    }

    @Transactional("logsTransactionManager")
    protected void persistEvent(ProceedingJoinPoint pjp, long durationMs, Throwable failure) {
        try {
            HttpServletRequest request = null;
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletRequestAttributes) {
                request = servletRequestAttributes.getRequest();
            }

            var payload = new HashMap<String, Object>();
            payload.put("method", request == null ? null : request.getMethod());
            payload.put("path", request == null ? null : request.getRequestURI());
            payload.put("query", request == null ? null : request.getQueryString());
            payload.put("durationMs", durationMs);
            payload.put("controller", pjp.getSignature().toShortString());
            payload.put("status", failure == null ? "success" : "failure");
            if (failure != null) {
                payload.put("error", failure.getMessage());
            }

            var event = new EventLog();
            event.setMessage((request == null ? "API" : request.getMethod()) + " " + (request == null ? "" : request.getRequestURI()));
            event.setType(failure == null ? 0 : 1);
            event.setEventableType("http_request");
            event.setEventableId(request == null ? null : request.getHeader("X-Request-Id"));
            event.setStoreId(adminStoreContext.currentStoreIdOrNull());
            event.setData(objectMapper.writeValueAsString(payload));
            eventLogRepository.save(event);
        } catch (Exception ignored) {
        }
    }
}
