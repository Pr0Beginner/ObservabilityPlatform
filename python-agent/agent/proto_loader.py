import os
import sys
from pathlib import Path


def ensure_generated() -> Path:
    agent_dir = Path(__file__).resolve().parent
    generated = agent_dir / "generated"
    pb2 = generated / "incident_context_pb2.py"
    proto = Path(os.getenv("PROTO_FILE", agent_dir.parents[1] / "src" / "main" / "proto" / "incident_context.proto"))
    generated.mkdir(exist_ok=True)

    if not pb2.exists():
        from grpc_tools import protoc

        result = protoc.main(
            [
                "grpc_tools.protoc",
                f"-I{proto.parent}",
                f"--python_out={generated}",
                f"--grpc_python_out={generated}",
                str(proto),
            ]
        )
        if result != 0:
            raise RuntimeError(f"protoc failed with exit code {result}")

    generated_path = str(generated)
    if generated_path not in sys.path:
        sys.path.insert(0, generated_path)
    return generated
