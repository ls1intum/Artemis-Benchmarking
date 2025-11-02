package de.tum.cit.aet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.json.JsonMapper;

// Helper to trace Jackson serialization errors in response bodies
//@RestControllerAdvice
class BodyWriteTracing implements ResponseBodyAdvice<Object> {

    private final Logger log = LoggerFactory.getLogger(BodyWriteTracing.class);

    private final JsonMapper mapper;

    public BodyWriteTracing(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter rt, Class c) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter rt, MediaType mt, Class c, ServerHttpRequest req, ServerHttpResponse res) {
        if (body == null) {
            return null;
        }
        try {
            mapper.writeValueAsString(body);
        }   // dry-run serialization
        catch (Exception e) {
            log.error("Jackson failed for {} {} -> {}", req.getMethod(), req.getURI(), e, e);
            throw new org.springframework.http.converter.HttpMessageNotWritableException("Jackson", e);
        }
        return body; // let Spring write it normally
    }
}
