#!/usr/bin/env python3
"""Capture the NOUS debug UI on a clean Actions emulator via its real navigation."""

import argparse
import json
import re
import struct
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


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))


def tap(node):
    x0, y0, x1, y1 = bounds(node)
    shell("input", "tap", str((x0 + x1) // 2), str((y0 + y1) // 2))
    pause()


def select(identifier):
    tap(wait_for(identifier))


def back():
    shell("input", "keyevent", "KEYCODE_BACK")
    pause()


def controller_key(key):
    shell("input", "gamepad", "keyevent", key)
    pause()


def size():
    return tuple(map(int, re.findall(r"(\d+)x(\d+)", shell("wm", "size"))[-1]))


def viewport_size():
    screenshot = adb("exec-out", "screencap", "-p", binary=True)
    return struct.unpack_from(">II", screenshot, offset=16)


def select_in_landscape_settings(identifier):
    for _ in range(4):
        root, _ = tree()
        node = match(root, identifier)
        if node is not None:
            tap(node)
            return
        width, height = viewport_size()
        x = str(width * 2 // 3)
        shell("input", "swipe", x, str(height * 4 // 5), x, str(height * 2 // 5), "400")
        pause()
    raise RuntimeError(f"Missing landscape setting {identifier}")


def rotate(rotation):
    shell("wm", "user-rotation", "lock", str(rotation))
    landscape = rotation in (1, 3)
    for _ in range(20):
        width, height = viewport_size()
        if (width > height) == landscape:
            pause(1)
            return
        pause(0.75)
    raise RuntimeError(f"Emulator rotation {rotation} did not take effect ({width}x{height})")


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
    shell("wm", "fixed-to-user-rotation", "enabled")
    rotate(0)
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
    wait_for("nous_settings_hub_hint")
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
    select("deleteProfile")
    controller_key("KEYCODE_DPAD_DOWN")
    c.capture("dialog-profile-delete-controller")
    controller_key("KEYCODE_BUTTON_B")
    wait_for("profileActive")
    select("profileActive")
    back()

    select("nous_nav_computers")
    wait_for("profilesButton", labels=("NOUS emulator",))
    shell("am", "force-stop", PACKAGE)
    shell("am", "start", "-W", "-n", PACKAGE + "/com.limelight.PcView")
    wait_for("nous_nav_computers")
    wait_for("profilesButton", labels=("NOUS emulator",))
    c.capture("computers-active-profile-after-restart")

    select("nous_nav_settings")
    select("nous_settings_development")
    tap(wait_for(labels=("Biblioteca de jogos offline", "Offline games library")))
    c.capture("settings-development")
    back()

    select("nous_nav_games")
    wait_for("appListText", labels=("Biblioteca offline", "Offline library"))
    wait_for(labels=("Genshin Impact",))
    c.capture("games-offline-library")

    select("nous_filter_running")
    wait_for(labels=("Cyberpunk 2077",))
    c.capture("games-offline-running")
    select("nous_filter_all")
    select("nous_offline_launchers")
    wait_for(labels=("Steam Big Picture",))
    c.capture("games-offline-launchers")
    select("nous_offline_desktop")
    wait_for("grid_text", labels=("Desktop",))
    c.capture("games-offline-desktop")
    select("nous_offline_games")

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
    controller_key("KEYCODE_BUTTON_B")
    wait_for("nous_help_content")
    tap(wait_for(labels=("Créditos e licenças", "Credits and licenses")))
    shell("input", "dpad", "keyevent", "KEYCODE_DPAD_DOWN")
    controller_key("KEYCODE_BUTTON_A")
    wait_for("nous_help_content")
    controller_key("KEYCODE_BUTTON_B")

    wait_for("nous_settings_video")
    rotate(1)
    wait_for("nous_settings_video")
    root, hierarchy = tree()
    if match(root, "nous_settings_hub_hint") is not None:
        raise RuntimeError("Settings subtitle should be hidden in landscape")
    c.capture("settings-landscape", hierarchy)
    select("nous_settings_video")
    wait_for("nous_section_title")
    c.capture("video-settings-landscape")
    select("nous_back")

    select("nous_settings_profiles")
    wait_for("addProfileFab")
    c.capture("profiles-landscape")
    rotate(0)
    wait_for("addProfileFab")
    rotate(1)
    wait_for("addProfileFab")
    select("addProfileFab")
    wait_for("nous_profile_name")
    wait_for("nous_save_profile")
    c.capture("profile-editor-landscape")
    back()
    root, _ = tree()
    if match(root, "nous_profile_name") is not None:
        back()
    wait_for("addProfileFab")
    select("nous_back")

    select_in_landscape_settings("nous_settings_diagnostics")
    wait_for("bt_update_gamepad")
    c.capture("device-diagnostics-landscape")
    select("nous_back")
    select_in_landscape_settings("nous_settings_help")
    wait_for("nous_help_content")
    c.capture("help-landscape")
    select("nous_back")

    select("nous_nav_computers")
    c.capture("computers-landscape")
    rotate(0)
    wait_for("manuallyAddPc")
    rotate(1)
    wait_for("manuallyAddPc")
    select("manuallyAddPc")
    wait_for("hostTextView")
    address_action = bounds(wait_for("addPcButton"))
    address_back = bounds(wait_for("nous_back"))
    if abs((address_action[1] + address_action[3]) - (address_back[1] + address_back[3])) > 4:
        raise RuntimeError("Manual connection action must be in the landscape header")
    c.capture("manual-connection-landscape")
    select("nous_back")
    wait_for("nous_nav_computers")
    select("nous_nav_games")
    wait_for(labels=("Genshin Impact",))
    c.capture("games-offline-landscape")

    root, _ = tree()
    destinations = [wait_for(target) for target in ("nous_nav_computers", "nous_nav_games", "nous_nav_settings")]
    rail_bounds = [bounds(node) for node in destinations]
    title_bounds = bounds(match(root, "appListText"))
    search_action_bounds = bounds(wait_for("nous_library_search_toggle"))
    if not (rail_bounds[-1][3] <= search_action_bounds[1]
            and rail_bounds[-1][0] <= search_action_bounds[0] < search_action_bounds[2] <= rail_bounds[-1][2]):
        raise RuntimeError("Landscape search action must be inside the navigation rail")
    if not (rail_bounds[0][1] < rail_bounds[1][1] < rail_bounds[2][1]
            and all(box[2] <= title_bounds[0] for box in rail_bounds)):
        raise RuntimeError("Landscape navigation must be vertical and left of the library")
    if match(root, "nous_game_search") is not None:
        raise RuntimeError("Landscape search should start collapsed")

    category_ids = ("nous_offline_desktop", "nous_offline_games", "nous_offline_launchers")
    select("nous_offline_desktop")
    root, hierarchy = tree()
    categories = [bounds(match(root, target)) for target in category_ids]
    centers = [box[1] + box[3] for box in categories]
    if max(centers) - min(centers) > 2:
        raise RuntimeError("Landscape library categories must stay on the same row")
    c.capture("games-offline-landscape-desktop", hierarchy)
    select("nous_offline_games")

    select("nous_library_search_toggle")
    wait_for("nous_game_search")
    shell("input", "text", "Genshin")
    wait_for("nous_game_search", labels=("Genshin",))
    c.capture("games-offline-landscape-search")
    select("nous_library_search_toggle")
    root, _ = tree()
    if match(root, "nous_game_search") is not None or match(root, labels=("Cyberpunk 2077",)) is not None:
        raise RuntimeError("Closing landscape search should retain its filter")
    wait_for(labels=("Genshin Impact",))

    rotate(0)
    wait_for("nous_game_search", labels=("Genshin",))
    c.capture("games-offline-search-after-rotation")

    capture_virtual_controller_editor(c)


def capture_virtual_controller_editor(c):
    select("nous_nav_settings")
    select("nous_settings_virtual_controller")
    wait_for("nous_osc_size_value", labels=("Tamanho · 100%", "Size · 100%"))
    root, hierarchy = tree()
    dpad_labels = ("Direcional", "D-pad")
    dpad = next(node for node in root.iter("node") if node.get("content-desc") in dpad_labels)
    original_dpad = bounds(dpad)
    c.capture("virtual-controller-editor", hierarchy)
    x0, y0, x1, y1 = original_dpad
    x, y = (x0 + x1) // 2, (y0 + y1) // 2
    shell("input", "swipe", str(x), str(y), str(x + (x1 - x0)), str(y - (y1 - y0) // 2), "450")
    root, _ = tree()
    moved_dpad = next(node for node in root.iter("node") if node.get("content-desc") in dpad_labels)
    if bounds(moved_dpad)[0] <= x0:
        raise RuntimeError("Dragging a controller button should update its position")

    x0, y0, x1, y1 = bounds(wait_for("nous_osc_size"))
    shell("input", "tap", str(x0 + (x1 - x0) * 3 // 4), str((y0 + y1) // 2))
    size_label = wait_for("nous_osc_size_value").get("text")
    if "100%" in size_label:
        raise RuntimeError("Resizing a controller button should update its displayed size")
    select("nous_osc_visible")
    rotate(1)
    wait_for("nous_osc_size_value", labels=(size_label,))
    if wait_for("nous_osc_visible").get("checked") != "false":
        raise RuntimeError("Controller editor draft should survive rotation")
    c.capture("virtual-controller-editor-landscape")
    select("nous_osc_save")

    select_in_landscape_settings("nous_settings_virtual_controller")
    wait_for("nous_osc_size_value", labels=(size_label,))
    if wait_for("nous_osc_visible").get("checked") != "false":
        raise RuntimeError("Controller button visibility should persist after saving")
    select("nous_back")


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
            (args.output / "crash-logcat.txt").write_text(adb("logcat", "-b", "crash", "-d"))
        except subprocess.SubprocessError:
            pass
        try:
            captures.capture("failure-state")
        except Exception:
            pass
        raise
    finally:
        captures.write_report()


if __name__ == "__main__":
    main()
