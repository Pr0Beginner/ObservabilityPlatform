import unittest
from dataclasses import dataclass
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from agent.diagnoser import diagnose


@dataclass
class Log:
    message: str
    timestamp: str = "2026-01-01T00:00:00Z"
    trace_id: str = "trace-1"


class DiagnoserTest(unittest.TestCase):
    def test_identifies_connection_failure(self):
        result = diagnose("orders", [Log("database connection timed out after 3000ms")])

        self.assertGreater(result.confidence, 0.8)
        self.assertIn("connection", result.root_cause)
        self.assertTrue(result.evidence)


if __name__ == "__main__":
    unittest.main()
