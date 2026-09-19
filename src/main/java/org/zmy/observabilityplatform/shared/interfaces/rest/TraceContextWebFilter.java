package org.zmy.observabilityplatform.shared.interfaces.rest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.zmy.observabilityplatform.shared.tracing.TraceContext;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextWebFilter implements WebFilter {
    public static final String RESPONSE_HEADER = "X-Trace-Id";
    public static final String EXCHANGE_ATTRIBUTE = TraceContext.class.getName();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst("traceparent");
        TraceContext context = TraceContext.continueFrom(incoming).orElseGet(TraceContext::root);
        exchange.getAttributes().put(EXCHANGE_ATTRIBUTE, context);
        exchange.getResponse().getHeaders().set(RESPONSE_HEADER, context.getTraceId());
        exposeTraceHeader(exchange.getResponse().getHeaders());
        return chain.filter(exchange).contextWrite(reactorContext -> reactorContext.put(TraceContext.class, context));
    }

    private void exposeTraceHeader(HttpHeaders headers) {
        if (!headers.getAccessControlExposeHeaders().contains(RESPONSE_HEADER)) {
            headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, RESPONSE_HEADER);
        }
    }
}
