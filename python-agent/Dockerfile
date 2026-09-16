FROM python:3.10-slim

WORKDIR /app
COPY python-agent/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY src/main/proto/incident_context.proto /app/proto/incident_context.proto
COPY python-agent/agent /app/agent
ENV PYTHONPATH=/app \
    PROTO_FILE=/app/proto/incident_context.proto

RUN python -c "from agent.proto_loader import ensure_generated; ensure_generated()"

CMD ["python", "-m", "agent.app"]
