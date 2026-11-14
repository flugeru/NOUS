#!/usr/bin/env python3
"""Analyze per-frame CSV timestamps; never subtract host and client clocks."""
import argparse
import csv
import json
import math
import statistics
from pathlib import Path

HOST = ("capture_ms", "encode_end_ms", "send_ms")
CLIENT = ("receive_ms", "decode_end_ms", "display_ms")
STAGES = HOST + CLIENT


def distribution(values):
    if not values:
        return None
    ordered = sorted(values)

    def percentile(p):
        index = (len(ordered) - 1) * p
        lo = math.floor(index)
        return ordered[lo] + (ordered[math.ceil(index)] - ordered[lo]) * (index - lo)

    return {
        "count": len(values),
        "min_ms": ordered[0],
        "mean_ms": statistics.fmean(values),
        "p50_ms": percentile(0.50),
        "p95_ms": percentile(0.95),
        "p99_ms": percentile(0.99),
        "max_ms": ordered[-1],
        "stddev_ms": statistics.pstdev(values),
    }


def analyze(stream, fps):
    if not math.isfinite(fps) or fps <= 0:
        raise ValueError("fps must be finite and positive")
    reader = csv.DictReader(stream)
    fields = reader.fieldnames or []
    if "frame_id" not in fields or not set(STAGES).intersection(fields):
        raise ValueError("CSV needs frame_id and at least one timestamp column: " + ", ".join(STAGES))
    if len(fields) != len(set(fields)):
        raise ValueError("duplicate CSV column")
    rows = []
    for line, row in enumerate(reader, 2):
        try:
            if None in row or any(value is None for value in row.values()):
                raise ValueError("wrong number of columns")
            frame_id = int(row["frame_id"])
            if frame_id < 0:
                raise ValueError("negative frame_id")
            stamps = {key: float(row[key]) for key in STAGES if row.get(key, "").strip()}
            if not stamps or any(not math.isfinite(value) or value < 0 for value in stamps.values()):
                raise ValueError("timestamps must be nonnegative finite milliseconds")
            for domain in (HOST, CLIENT):
                available = [stamps[key] for key in domain if key in stamps]
                if available != sorted(available):
                    raise ValueError("pipeline timestamps go backwards within one clock domain")
            rows.append((frame_id, stamps))
        except (ValueError, TypeError) as exc:
            raise ValueError(f"CSV line {line}: {exc}") from exc
    if not rows:
        raise ValueError("CSV contains no frames")
    target = 1000 / fps
    if not math.isfinite(target):
        raise ValueError("fps produces a non-finite frame period")
    result = {"schema_version": 1, "fps": fps, "rows": len(rows), "stages": {}, "latencies": {}}
    for stage in STAGES:
        samples = [(frame_id, stamps[stage]) for frame_id, stamps in rows if stage in stamps]
        if not samples:
            continue
        intervals = []
        missing_ids = repeated_ids = equal_timestamps = 0
        seen = set()
        for index, (frame_id, stamp) in enumerate(samples):
            if index:
                last_id, last_stamp = samples[index - 1]
                if stamp < last_stamp or frame_id < last_id:
                    raise ValueError(f"{stage}: timestamps or frame IDs go backwards; split sessions first")
                intervals.append(stamp - last_stamp)
                missing_ids += max(0, frame_id - last_id - 1)
                equal_timestamps += stamp == last_stamp
            repeated_ids += frame_id in seen
            seen.add(frame_id)
        elapsed = samples[-1][1] - samples[0][1]
        result["stages"][stage] = {
            "frames": len(samples),
            "intervals": distribution(intervals),
            "absolute_interval_error": distribution([abs(value - target) for value in intervals]),
            "intervals_over_1_5x_target": sum(value > 1.5 * target for value in intervals),
            "repeated_frame_ids": repeated_ids,
            "unobserved_frame_ids": missing_ids,
            "equal_timestamps": equal_timestamps,
            "observed_fps": (len(samples) - 1) * 1000 / elapsed if elapsed > 0 else None,
        }
    # Only same-clock deltas. send_ms -> receive_ms is intentionally forbidden.
    for start, end in (("capture_ms", "encode_end_ms"), ("encode_end_ms", "send_ms"),
                       ("capture_ms", "send_ms"), ("receive_ms", "decode_end_ms"),
                       ("decode_end_ms", "display_ms"), ("receive_ms", "display_ms")):
        values = [stamps[end] - stamps[start] for _, stamps in rows if start in stamps and end in stamps]
        if values:
            result["latencies"][start + "_to_" + end] = distribution(values)
    return result


def compare(current, baseline):
    if baseline.get("schema_version") != current["schema_version"] or baseline.get("fps") != current["fps"]:
        raise ValueError("baseline must use same schema version and target fps")
    deltas = {}
    for stage, data in current["stages"].items():
        previous = baseline.get("stages", {}).get(stage, {}).get("intervals")
        if data["intervals"] and previous:
            deltas[stage] = {key: data["intervals"][key] - previous[key]
                             for key in ("p50_ms", "p95_ms", "p99_ms", "max_ms", "stddev_ms")}
    return deltas


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path)
    parser.add_argument("--fps", type=float, required=True)
    parser.add_argument("--baseline", type=Path, help="JSON from earlier run at same target fps")
    parser.add_argument("--output", type=Path, help="new JSON file; existing files are never overwritten")
    args = parser.parse_args()
    try:
        if args.output and (args.output.resolve() == args.trace.resolve() or
                            (args.baseline and args.output.resolve() == args.baseline.resolve())):
            raise ValueError("output must differ from input and baseline")
        with args.trace.open(newline="", encoding="utf-8-sig") as stream:
            result = analyze(stream, args.fps)
        if args.baseline:
            result["interval_delta_ms"] = compare(result, json.loads(args.baseline.read_text(encoding="utf-8-sig")))
        text = json.dumps(result, indent=2, allow_nan=False) + "\n"
        if args.output:
            with args.output.open("x", encoding="utf-8") as output:
                output.write(text)
        else:
            print(text, end="")
    except (OSError, ValueError, KeyError, TypeError) as exc:
        parser.exit(2, f"error: {exc}\n")


if __name__ == "__main__":
    main()
