#!/usr/bin/env python3
"""Run: python android/tools/test_capture_nous_android.py"""
from xml.etree import ElementTree as ET
from capture_nous_android import immersive_confirmation


def hierarchy(package, identifier):
    return ET.fromstring(f'<hierarchy><node package="{package}" resource-id="{identifier}" text="Got it" /></hierarchy>')


assert immersive_confirmation(hierarchy("com.android.systemui", "com.android.systemui:id/ok")) is not None
assert immersive_confirmation(hierarchy("com.flugel.nous", "com.flugel.nous:id/ok")) is None
assert immersive_confirmation(hierarchy("com.android.systemui", "android:id/button1")) is None
framework_hint = ET.fromstring('<hierarchy><node resource-id="android:id/immersive_cling_title" /><node package="android" resource-id="android:id/ok" /></hierarchy>')
assert immersive_confirmation(framework_hint) is not None
assert immersive_confirmation(hierarchy("android", "android:id/ok")) is None
print("Fullscreen hint recognition: passed")
