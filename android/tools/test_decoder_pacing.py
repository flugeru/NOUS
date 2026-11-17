"""Source regression checks for renderer paths that cannot run without MediaCodec hardware.
Run: python android/tools/test_decoder_pacing.py
"""
from pathlib import Path

source = (Path(__file__).resolve().parents[1] / 'app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java').read_text(encoding='utf-8')
assert 'WATCHDOG_C2_SLEEP' not in source
assert 'LATEST_ONLY_LOW_LATENCY' not in source
assert 'dropThresholdSmoothNs' not in source
assert source.count('videoDecoder.flush()') == 1, 'flush must stay inside coordinated recovery'
recovery = source.index('videoDecoder.flush()')
assert source.rfind('codecRecoveryThreadQuiescedFlags == CR_FLAG_ALL', 0, recovery) >= 0
assert 'synchronized (enqueueNsByPtsUs)' in source
print('Decoder pacing regression checks passed')
