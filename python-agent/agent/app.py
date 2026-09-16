import json
import logging
import os
import uuid
from datetime import datetime, timezone

import grpc
from kafka import KafkaConsumer, KafkaProducer

from .diagnoser import diagnose
from .proto_loader import ensure_generated

ensure_generated()
import incident_context_pb2  # noqa: E402
import incident_context_pb2_grpc  # noqa: E402


logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
LOGGER = logging.getLogger("diagnosis-agent")


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def completion(event: dict, result=None, error: str | None = None) -> dict:
    return {
        "eventId": str(uuid.uuid4()),
        "taskId": event["taskId"],
        "incidentId": event["incidentId"],
        "version": event.get("version", 1),
        "rootCause": result.root_cause if result else "",
        "confidence": result.confidence if result else 0.0,
        "evidence": result.evidence if result else [],
        "recommendations": result.recommendations if result else [],
        "toolCalls": [f"IncidentContextService.GetIncidentContext({event['incidentId']})"],
        "completedAt": utc_now(),
        "error": error,
    }


def run() -> None:
    brokers = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092").split(",")
    requested_topic = os.getenv("DIAGNOSIS_REQUESTED_TOPIC", "diagnosis.requested.v1")
    completed_topic = os.getenv("DIAGNOSIS_COMPLETED_TOPIC", "diagnosis.completed.v1")
    grpc_target = os.getenv("JAVA_GRPC_TARGET", "localhost:9090")

    consumer = KafkaConsumer(
        requested_topic,
        bootstrap_servers=brokers,
        group_id=os.getenv("KAFKA_GROUP_ID", "python-diagnosis-agent"),
        auto_offset_reset="earliest",
        enable_auto_commit=False,
        value_deserializer=lambda value: json.loads(value.decode("utf-8")),
    )
    producer = KafkaProducer(
        bootstrap_servers=brokers,
        key_serializer=lambda value: value.encode("utf-8"),
        value_serializer=lambda value: json.dumps(value).encode("utf-8"),
    )
    channel = grpc.insecure_channel(grpc_target)
    stub = incident_context_pb2_grpc.IncidentContextServiceStub(channel)

    LOGGER.info("Agent ready: topic=%s grpc=%s", requested_topic, grpc_target)
    for message in consumer:
        event = message.value
        try:
            context = stub.GetIncidentContext(
                incident_context_pb2.IncidentContextRequest(
                    incident_id=event["incidentId"], log_limit=100
                ),
                timeout=15,
            )
            result = diagnose(context.service, context.logs)
            payload = completion(event, result=result)
        except Exception as exception:  # task failures must be reported to Java
            LOGGER.exception("Diagnosis failed for task %s", event.get("taskId"))
            payload = completion(event, error=str(exception))

        producer.send(completed_topic, key=event["taskId"], value=payload).get(timeout=15)
        consumer.commit()
        LOGGER.info("Diagnosis completed: task=%s", event["taskId"])


if __name__ == "__main__":
    run()
