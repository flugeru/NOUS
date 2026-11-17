"""Regression check: embedded library must restart host polling after pairing."""
from pathlib import Path

source = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/limelight/PcView.java").read_text()
success = source.split("if (toastSuccess) {", 1)[1].split("else {", 1)[0]
assert success.index("startComputerUpdates();") < success.index("doAppList(computer, true, false);")
print("PASS: successful pairing restarts host polling before opening embedded library")
