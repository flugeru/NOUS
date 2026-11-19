"""Source regression checks for renderer paths that cannot run without MediaCodec hardware.
Run: python android/tools/test_decoder_pacing.py
"""
from pathlib import Path
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[1] / 'app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java').read_text(encoding='utf-8')
assert 'WATCHDOG_C2_SLEEP' not in source
assert 'LATEST_ONLY_LOW_LATENCY' not in source
assert 'dropThresholdSmoothNs' not in source
assert source.count('videoDecoder.flush()') == 1, 'flush must stay inside coordinated recovery'
recovery = source.index('videoDecoder.flush()')
assert source.rfind('codecRecoveryThreadQuiescedFlags == CR_FLAG_ALL', 0, recovery) >= 0
assert 'synchronized (enqueueNsByPtsUs)' in source
assert 'while ((outIndex = videoDecoder.dequeueOutputBuffer(info, 0)) >= 0)' in source
assert 'while ((outIndex = videoDecoder.dequeueOutputBuffer(info, getOutputDequeueTimeoutUs()))' not in source

# Replay the actual production drain body, not a second implementation.
start = source.index('while ((outIndex = videoDecoder.dequeueOutputBuffer(info, 0)) >= 0)')
end = source.index('\n                                }', start) + len('\n                                }')
drain = source[start:end]
harness = '''
class DrainCheck {
    static class Info { long presentationTimeUs; }
    static class Decoder {
        long now;
        int next = 1, released;
        int dequeueOutputBuffer(Info info, long timeout) {
            if (next <= 2) { info.presentationTimeUs = next; return next++; }
            long due = (next - 2) * 1000L;
            if (next <= 12 && due <= now + timeout) {
                now = due; info.presentationTimeUs = next; return next++;
            }
            now += timeout;
            return -1;
        }
        void releaseOutputBuffer(int index, boolean render) {
            if (render || index != released++) throw new AssertionError("Wrong release");
        }
    }
    public static void main(String[] args) {
        Decoder videoDecoder = new Decoder();
        Info info = new Info();
        int outIndex, lastIndex = 0, numFramesOut = 1;
        long presentationTimeUs = 0;
        boolean frameDropped = false;
        DRAIN_BODY
        if (videoDecoder.now != 0 || lastIndex != 2 || numFramesOut != 3 ||
            videoDecoder.released != 2 || !frameDropped || presentationTimeUs != 2)
            throw new AssertionError("Ready frame delayed or ownership lost");
    }
}
'''
with tempfile.TemporaryDirectory() as directory:
    java = Path(directory) / 'DrainCheck.java'
    java.write_text(harness.replace('DRAIN_BODY', drain), encoding='utf-8')
    subprocess.run(['java', str(java)], check=True)
    # Negative control: original blocking drain must fail with future output.
    java.write_text(harness.replace('DRAIN_BODY', drain.replace(
        'dequeueOutputBuffer(info, 0)', 'dequeueOutputBuffer(info, 2000)')), encoding='utf-8')
    result = subprocess.run(['java', str(java)], capture_output=True, text=True)
    assert result.returncode != 0 and 'Ready frame delayed' in result.stderr, result.stderr
print('Decoder pacing regression checks passed (production drain and negative control)')
