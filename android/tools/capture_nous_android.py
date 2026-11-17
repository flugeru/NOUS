#!/usr/bin/env python3
"""Capture the NOUS debug UI on a clean Actions emulator via its real navigation."""

import argparse
import json
import re
import subprocess
import time
import unicodedata
from pathlib import Path
from xml.etree import ElementTree as ET

PACKAGE = "com.flugel.nous"
DUMP = "/sdcard/nous-window.xml"


def adb(*args, binary=False):
    return subprocess.run(["adb", *args], check=True, capture_output=True,
                          text=not binary, timeout=45).stdout


def shell(*args):
    return adb("shell", *args)


def pause(seconds=0.45):
    time.sleep(seconds)


def tree():
    for _ in range(3):
        shell("uiautomator", "dump", DUMP)
        data = adb("exec-out", "cat", DUMP, binary=True)
        try:
            return ET.fromstring(data), data
        except ET.ParseError:
            pause()
    raise RuntimeError("UI Automator did not return an XML hierarchy")


def normalized(text):
    return "".join(c for c in unicodedata.normalize("NFKD", text.casefold())
                   if not unicodedata.combining(c))


def match(root, identifier=None, labels=()):
    for node in root.iter("node"):
        if identifier and node.get("resource-id") != f"{PACKAGE}:id/{identifier}":
            continue
        text = normalized(node.get("text", ""))
        description = normalized(node.get("content-desc", "")) if identifier else ""
        if not labels or any(normalized(label) in (text, description) for label in labels):
            return node
    return None


def wait_for(identifier=None, labels=()):
    for _ in range(5):
        root, _ = tree()
        node = match(root, identifier, labels)
        if node is not None:
            return node
        pause()
    visible = [n.get("text") for n in root.iter("node") if n.get("text")]
    raise RuntimeError(f"Missing UI target {identifier or labels}; visible labels: {visible}")


def tap(node):
    x0, y0, x1, y1 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    shell("input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2))
    pause()


def select(identifier):
    tap(wait_for(identifier))


def back():
    shell("input", "keyevent", "KEYCODE_BACK")
    pause()


def size():
    return tuple(map(int, re.findall(r"(\d+)x(\d+)", shell("wm", "size"))[-1]))


class Captures:
    def __init__(self, output):
        self.output = output
        output.mkdir(parents=True, exist_ok=True)
        self.screens = []
        self.optional_missing = []

    def capture(self, name, hierarchy=None):
        if hierarchy is None:
            _, hierarchy = tree()
        raw = adb("exec-out", "screencap", "-p", binary=True)
        if not raw.startswith(b"\x89PNG"):
            raise RuntimeError(f"Empty screencap: {name}")
        stem = f"{len(self.screens) + 1:02d}-{name}"
        (self.output / (stem + ".png")).write_bytes(raw)
        (self.output / (stem + ".xml")).write_bytes(hierarchy)
        self.screens.append(stem)
        print(stem, flush=True)

    def walk_list(self, page, expected_dialogs=(), max_pages=20):
        visited = set()
        shown = set()
        width, height = size()
        for index in range(1, max_pages + 1):
            root, hierarchy = tree()
            key = tuple((n.get("resource-id"), n.get("text"), n.get("bounds"))
                        for n in root.iter("node") if n.get("text"))
            if key in visited:
                break
            visited.add(key)
            self.capture(f"{page}-{index:02d}", hierarchy)

            for name, labels in expected_dialogs:
                if name in shown:
                    continue
                node = match(root, labels=labels)
                if node is None:
                    continue
                tap(node)
                dialog, data = tree()
                if any(n.get("resource-id") in {"android:id/button1", "android:id/button2",
                        f"{PACKAGE}:id/button1", f"{PACKAGE}:id/button2"}
                       for n in dialog.iter("node")):
                    self.capture(f"dialog-{name}", data)
                    shown.add(name)
                    back()
                    root, _ = tree()
                else:
                    root = dialog
            shell("input", "swipe", str(width // 2), str(height * 4 // 5),
                  str(width // 2), str(height // 3), "480")
            pause()
        else:
            self.optional_missing.append(page + ": reached screenshot limit before end")
        self.optional_missing.extend(name for name, _ in expected_dialogs if name not in shown)

    def write_report(self):
        report = {
            "source": "Installed NOUS x86_64 debug APK, Android emulator UI",
            "screenshots": self.screens,
            "dialogs_not_found_or_device_dependent": self.optional_missing,
            "requires_a_paired_host": [
                "host list with online/offline PC rows", "pairing PIN",
                "game library/search/running filter", "full-screen streaming/HUD",
                "session menu", "virtual-controller editor during a session",
                "external-display controller", "connection interrupted during streaming",
            ],
        }
        (self.output / "coverage.json").write_text(json.dumps(report, indent=2) + "\n")


def run(c):
    shell("wm", "size", "1080x2340")
    shell("wm", "density", "440")
    shell("settings", "put", "system", "accelerometer_rotation", "0")
    shell("settings", "put", "system", "user_rotation", "0")
    shell("cmd", "locale", "set-app-locales", PACKAGE, "--user", "0", "--locales", "pt-BR")
    width, height = size()
    for attempt in range(2):
        shell("am", "start", "-W", "-a", "android.intent.action.MAIN",
              "-c", "android.intent.category.HOME")
        pause(5)
        shell("input", "swipe", str(width // 2), str(height * 4 // 5),
              str(width // 2), str(height // 5), "500")
        try:
            wait_for(labels=("NOUS",))
        except RuntimeError:
            root, _ = tree()
            launcher_anr = match(root, labels=("Pixel Launcher isn't responding",))
            close = match(root, labels=("Close app",))
            if launcher_anr is None or close is None:
                raise
            tap(close)
            if attempt == 1:
                c.optional_missing.append("launcher-app-drawer: Pixel Launcher did not respond")
        else:
            c.capture("launcher-app-drawer")
            break
    shell("am", "start", "-W", "-n", PACKAGE + "/com.limelight.PcView")
    wait_for("nous_nav_computers")
    c.capture("computers-on-launch")
    pause(7)
    c.capture("computers-no-host")
    select("manuallyAddPc")
    wait_for("hostTextView")
    c.capture("manual-connection")
    back()

    select("nous_nav_settings")
    wait_for("nous_settings_video")
    c.capture("settings-hub")
    select("nous_settings_video")
    wait_for("nous_section_title")
    c.walk_list("video-audio", [
        ("resolution", ("Resolução", "Video resolution")),
        ("fps", ("Taxa de quadros por segundo", "Video frame rate")),
        ("bitrate", ("Taxa de bits", "Video bitrate")),
        ("pacing", ("Ritmo de quadros", "Ritmo de quadros de vídeo", "Video frame pacing", "Frame pacing")),
        ("codec", ("Formato de vídeo", "Codec de vídeo", "Video codec", "Video format", "Change codec settings")),
        ("audio", ("Configurações de som Surround", "Surround sound configuration")),
    ])
    back()

    select("nous_settings_controls")
    wait_for("nous_section_title")
    c.walk_list("controls")
    back()
    select("nous_settings_advanced")
    wait_for("nous_section_title")
    c.walk_list("advanced")
    back()

    select("nous_settings_profiles")
    wait_for("addProfileFab")
    c.capture("profiles-initial")
    select("addProfileFab")
    wait_for("nous_profile_name")
    c.capture("profile-editor")
    select("nous_profile_name")
    shell("input", "text", "NOUS%semulator")
    # The hardware keyboard is usable even when no software IME is showing.
    # dumpsys has its own timeout, independent of the adb subprocess timeout.
    # Retry a temporary service timeout before deciding whether to press Back.
    for attempt in range(3):
        try:
            status = shell("dumpsys", "-t", "20", "input_method")
            break
        except subprocess.CalledProcessError as exc:
            if attempt == 2:
                detail = (exc.stderr or exc.stdout or "").strip()
                raise RuntimeError(f"Could not inspect emulator keyboard state: {detail}") from exc
            pause(1)
    if re.search(r"(?:mInputShown|mIsInputViewShown)=true", status):
        back()
    c.walk_list("profile-preferences", max_pages=48)
    select("nous_save_profile")
    wait_for("addProfileFab")
    c.capture("profiles-with-example")
    select("profileActive")
    back()

    select("nous_nav_computers")
    wait_for("profilesButton", labels=("NOUS emulator",))
    shell("am", "force-stop", PACKAGE)
    shell("am", "start", "-W", "-n", PACKAGE + "/com.limelight.PcView")
    wait_for("nous_nav_computers")
    wait_for("profilesButton", labels=("NOUS emulator",))
    c.capture("computers-active-profile-after-restart")

    select("nous_nav_games")
    wait_for("appListText", labels=("Biblioteca offline", "Offline library"))
    wait_for(labels=("Circuito Noturno", "Night Circuit"))
    c.capture("games-offline-library")

    select("nous_filter_running")
    wait_for(labels=("Estação Orbital", "Orbital Station"))
    c.capture("games-offline-running")
    select("nous_filter_all")

    select("nous_nav_settings")
    select("nous_settings_diagnostics")
    wait_for("nous_copy_diagnostics")
    c.walk_list("device-diagnostics")
    back()

    select("nous_settings_help")
    wait_for("nous_help_content")
    c.capture("help")
    tap(wait_for(labels=("Créditos e licenças", "Credits and licenses")))
    c.capture("dialog-credits")
    back()
    back()

    wait_for("nous_nav_computers")
    shell("settings", "put", "system", "user_rotation", "1")
    pause(2)
    c.capture("settings-landscape")
    select("nous_nav_computers")
    c.capture("computers-landscape")
    select("nous_nav_games")
    wait_for(labels=("Circuito Noturno", "Night Circuit"))
    c.capture("games-offline-landscape")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    captures = Captures(args.output)
    try:
        adb("install", "-r", str(args.apk))
        run(captures)
    except (RuntimeError, subprocess.SubprocessError, ValueError) as exc:
        (args.output / "failure.txt").write_text(str(exc) + "\n")
        try:
            captures.capture("failure-state")
        except Exception:
            pass
        raise
    finally:
        captures.write_report()


if __name__ == "__main__":
    main()
