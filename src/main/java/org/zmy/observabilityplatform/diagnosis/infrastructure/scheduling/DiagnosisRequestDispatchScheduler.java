package org.zmy.observabilityplatform.diagnosis.infrastructure.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.diagnosis.application.service.DiagnosisRequestDispatcher;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class DiagnosisRequestDispatchScheduler {
    private final DiagnosisRequestDispatcher dispatcher;
    private final AtomicBoolean running = new AtomicBoolean();

    public DiagnosisRequestDispatchScheduler(DiagnosisRequestDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${app.diagnosis.dispatch-delay-ms:1000}")
    public void dispatch() {
        if (running.compareAndSet(false, true)) {
            dispatcher.dispatch(100).doFinally(signal -> running.set(false))
                    .subscribe(null, error -> log.error("Cannot read diagnosis request outbox", error));
        }
    }
}
