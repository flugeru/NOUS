"""Source regression: Warp FPS must not scale negotiated bandwidth.
Run: python scripts/tests/test_warp_bitrate.py
"""
from pathlib import Path
import re

source = (Path(__file__).resolve().parents[2] / 'src/rtsp.cpp').read_text(encoding='utf-8')
start = source.index('configuredBitrateKbps = util::from_view(args.at("x-ml-video.configuredBitrateKbps"sv))')
end = source.index('} catch (std::out_of_range &)', start)
negotiation = source[start:end]
assert 'configuredBitrateKbps = config::video.max_bitrate;' in negotiation
assert not re.search(r'configuredBitrateKbps\s*\*=', negotiation), 'FPS must not multiply bandwidth'
assert 'warp_factor' not in negotiation, 'Warp must not override explicit bandwidth or host cap'
print('Warp bitrate source regression checks passed')
