"""Run: python tools/latency/test_analyze_trace.py (stdlib only)."""
import io
import math
import unittest

from analyze_trace import analyze, compare, distribution


class TraceTests(unittest.TestCase):
    def test_regular_frames_and_clock_domains(self):
        report = analyze(io.StringIO(
            "frame_id,capture_ms,encode_end_ms,send_ms,receive_ms,decode_end_ms,display_ms\n"
            "0,1000,1002,1003,10,11,12\n1,1010,1012,1013,20,21,22\n"
            "2,1020,1022,1023,30,31,32\n"), 100)
        stage = report["stages"]["display_ms"]
        self.assertEqual(stage["observed_fps"], 100)
        self.assertEqual(stage["intervals"]["p99_ms"], 10)
        self.assertEqual(stage["absolute_interval_error"]["max_ms"], 0)
        self.assertEqual(report["latencies"]["capture_ms_to_send_ms"]["mean_ms"], 3)
        self.assertNotIn("send_ms_to_receive_ms", report["latencies"])
        self.assertEqual(compare(report, report)["display_ms"]["p99_ms"], 0)

    def test_gaps_duplicates_and_stall(self):
        report = analyze(io.StringIO("frame_id,display_ms\n0,0\n1,10\n1,20\n3,50\n"), 100)
        stage = report["stages"]["display_ms"]
        self.assertEqual(stage["repeated_frame_ids"], 1)
        self.assertEqual(stage["unobserved_frame_ids"], 1)
        self.assertEqual(stage["intervals_over_1_5x_target"], 1)
        self.assertEqual(stage["intervals"]["max_ms"], 30)

    def test_single_frame_and_optional_stages(self):
        report = analyze(io.StringIO("frame_id,display_ms,capture_ms\n0,0,\n"), 60)
        self.assertIsNone(report["stages"]["display_ms"]["intervals"])
        self.assertNotIn("capture_ms", report["stages"])
        self.assertIsNone(report["stages"]["display_ms"]["observed_fps"])

    def test_interpolated_percentiles(self):
        self.assertEqual(distribution([0, 10])["p95_ms"], 9.5)

    def test_invalid_inputs(self):
        cases = ["", "frame_id,display_ms\n", "frame_id,display_ms\n0,nan\n",
                 "frame_id,display_ms\n0,inf\n", "frame_id,display_ms\n-1,0\n",
                 "frame_id,display_ms\n0,-1\n", "frame_id,display_ms\nx,1\n",
                 "frame_id,display_ms\n0,2\n1,1\n", "frame_id,display_ms\n1,1\n0,2\n",
                 "frame_id,capture_ms,send_ms\n0,2,1\n", "frame_id,display_ms\n0,1,2\n",
                 "frame_id,display_ms\n0\n", "frame_id,display_ms,display_ms\n0,1,2\n",
                 "frame_id,display_ms\n0,\n"]
        for text in cases:
            with self.subTest(text=text), self.assertRaises(ValueError):
                analyze(io.StringIO(text), 60)
        for fps in (0, -1, math.nan, math.inf, 5e-324):
            with self.subTest(fps=fps), self.assertRaises(ValueError):
                analyze(io.StringIO("frame_id,display_ms\n0,0\n"), fps)

    def test_mismatched_baseline(self):
        report = analyze(io.StringIO("frame_id,display_ms\n0,0\n"), 60)
        with self.assertRaises(ValueError):
            compare(report, {"schema_version": 1, "fps": 120})


if __name__ == "__main__":
    unittest.main()
