package org.zmy.observabilityplatform.configuration;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.diagnosis.interfaces.grpc.IncidentContextGrpcService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.grpc.enabled", havingValue = "true", matchIfMissing = true)
public class GrpcServerLifecycle implements SmartLifecycle {
    private final IncidentContextGrpcService service;
    private final int port;
    private volatile Server server;
    private volatile boolean running;

    public GrpcServerLifecycle(IncidentContextGrpcService service,
                               @Value("${app.grpc.port:9090}") int port) {
        this.service = service;
        this.port = port;
    }

    @Override
    public void start() {
        try {
            server = ServerBuilder.forPort(port).addService(service).build().start();
            running = true;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start gRPC server on port " + port, exception);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (server != null) {
            server.shutdown();
            try {
                server.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
