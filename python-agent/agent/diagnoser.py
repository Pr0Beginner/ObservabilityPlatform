from dataclasses import dataclass
from typing import Iterable, Protocol


class LogLike(Protocol):
    message: str
    timestamp: str
    trace_id: str


@dataclass(frozen=True)
class Diagnosis:
    root_cause: str
    confidence: float
    evidence: list[str]
    recommendations: list[str]


def diagnose(service: str, logs: Iterable[LogLike]) -> Diagnosis:
    entries = list(logs)
    corpus = "\n".join(log.message.lower() for log in entries)
    evidence = [_evidence_line(log) for log in entries[:5]]

    if any(token in corpus for token in ("connection timed out", "connection timeout", "connection refused")):
        return Diagnosis(
            root_cause=f"{service} cannot establish a required downstream or database connection.",
            confidence=0.88,
            evidence=evidence,
            recommendations=[
                "Verify the downstream endpoint, DNS, network policy, and security-group connectivity.",
                "Check connection-pool saturation and downstream service health.",
                "Restore connectivity or roll back the latest connection configuration change.",
            ],
        )
    if any(token in corpus for token in ("outofmemoryerror", "out of memory", "java heap space")):
        return Diagnosis(
            root_cause=f"{service} is exhausting available process or JVM memory.",
            confidence=0.9,
            evidence=evidence,
            recommendations=[
                "Inspect heap, container limits, GC pressure, and recent allocation changes.",
                "Capture a heap dump before restart when operationally safe.",
                "Apply a temporary capacity increase and fix the allocation or retention source.",
            ],
        )
    if "nullpointerexception" in corpus:
        return Diagnosis(
            root_cause=f"{service} is dereferencing an unexpected null value in the application path.",
            confidence=0.82,
            evidence=evidence,
            recommendations=[
                "Locate the first application stack frame and reproduce with the same request context.",
                "Validate upstream payload and configuration assumptions.",
                "Add a guard and regression test before deploying the fix.",
            ],
        )
    return Diagnosis(
        root_cause=f"{service} is emitting a repeated error fingerprint; available evidence is insufficient for a specific cause.",
        confidence=0.55,
        evidence=evidence,
        recommendations=[
            "Inspect the full trace and logs immediately preceding the first occurrence.",
            "Compare the incident start time with deployment and configuration changes.",
            "Escalate to the service owner if the fingerprint persists.",
        ],
    )


def _evidence_line(log: LogLike) -> str:
    trace = f" trace={log.trace_id}" if log.trace_id else ""
    message = log.message.replace("\n", " ")[:500]
    return f"{log.timestamp}{trace} {message}"
