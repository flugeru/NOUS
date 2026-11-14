"""Run production NVENC bitrate reconfiguration with a fake driver; no GPU needed.

Extract the method verbatim, replacing only surrounding SDK/platform types. This
checks submitted VBV values and rollback, not a second copy of the arithmetic.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile


def fixture(root):
    text = (root / 'src/nvenc/nvenc_base.cpp').read_text()
    start = text.index('  bool nvenc_base::set_bitrate(')
    end = text.index('\n  bool nvenc_base::nvenc_failed(', start)
    return r'''
#include <algorithm>
#include <cassert>
#include <cstdint>
#include <iostream>
#include <sstream>
#include <string>
#define BOOST_LOG(level) std::ostringstream{}
constexpr int NV_ENC_CODEC_HEVC_GUID = 1;
bool equal_guids(int lhs, int rhs) { return lhs == rhs; }
namespace api { unsigned reconfigure_params_version(unsigned value) { return value; } }
struct RC { uint32_t averageBitRate{}, maxBitRate{}, vbvBufferSize{}; };
struct NV_ENC_CONFIG { RC rcParams; };
struct Init { int encodeGUID{}; NV_ENC_CONFIG *encodeConfig{}; };
struct NV_ENC_RECONFIGURE_PARAMS {
  unsigned version;
  Init reInitEncodeParams{};
  unsigned resetEncoder{}, forceIDR{};
};
struct Driver {
  unsigned calls = 0;
  bool fail = false;
  NV_ENC_CONFIG submitted{};
  unsigned reset{}, idr{};
  int nvEncReconfigureEncoder(void *, NV_ENC_RECONFIGURE_PARAMS *params) {
    ++calls;
    submitted = *params->reInitEncodeParams.encodeConfig;
    reset = params->resetEncoder;
    idr = params->forceIDR;
    return fail ? 1 : 0;
  }
};
struct nvenc_base {
  struct context_guard_t {
    explicit context_guard_t(nvenc_base &) {}
    explicit operator bool() const { return true; }
  };
  void *encoder = this;
  Driver driver;
  Driver *nvenc = &driver;
  Init saved_init_params{};
  NV_ENC_CONFIG current_enc_config{};
  unsigned selected_api_version = 13;
  std::string last_nvenc_error_string;
  bool nvenc_failed(int status) { return status != 0; }
  bool set_bitrate(int bitrate_kbps);
};
''' + text[start:end] + r'''
int main() {
  nvenc_base encoder;
  // 12 Mbps / 240 fps: 50,000 bits. Downshift must not inflate 4.17 ms to 50 ms.
  encoder.current_enc_config.rcParams = {12000000, 12000000, 50000};
  assert(encoder.set_bitrate(2000));
  assert(encoder.driver.submitted.rcParams.vbvBufferSize == 8333);
  assert(encoder.driver.submitted.rcParams.averageBitRate == 2000000);
  assert(!encoder.driver.reset && !encoder.driver.idr);
  assert(encoder.set_bitrate(12000));
  assert(encoder.driver.submitted.rcParams.vbvBufferSize == 49998);
  assert(encoder.driver.reset && encoder.driver.idr);
  // The same-rate update must not change the buffer either.
  encoder.current_enc_config.rcParams = {12000000, 12000000, 50000};
  assert(encoder.set_bitrate(12000));
  assert(encoder.driver.submitted.rcParams.vbvBufferSize == 50000);
  assert(!encoder.driver.reset && !encoder.driver.idr);
  // Retain user's explicit VBV increase, with only integer rounding loss.
  encoder.current_enc_config.rcParams = {12000000, 12000000, 75000};
  assert(encoder.set_bitrate(2000));
  assert(encoder.driver.submitted.rcParams.vbvBufferSize == 12500);
  encoder.current_enc_config.rcParams = {800000000, 800000000, 1};
  assert(encoder.set_bitrate(1));
  assert(encoder.driver.submitted.rcParams.vbvBufferSize == 1);
  // Unspecified VBV and unknown prior bitrate retain existing behavior.
  encoder.current_enc_config.rcParams = {12000000, 12000000, 0};
  assert(encoder.set_bitrate(2000));
  assert(encoder.driver.submitted.rcParams.vbvBufferSize == 0);
  encoder.current_enc_config.rcParams = {0, 0, 75};
  assert(encoder.set_bitrate(2000));
  assert(encoder.driver.submitted.rcParams.vbvBufferSize == 75);
  // Driver rejection must not persist tentative changes.
  encoder.current_enc_config.rcParams = {12000000, 12000000, 50000};
  encoder.driver.fail = true;
  assert(!encoder.set_bitrate(2000));
  assert(encoder.current_enc_config.rcParams.averageBitRate == 12000000);
  assert(encoder.current_enc_config.rcParams.maxBitRate == 12000000);
  assert(encoder.current_enc_config.rcParams.vbvBufferSize == 50000);
  const auto calls = encoder.driver.calls;
  assert(!encoder.set_bitrate(0));
  assert(!encoder.set_bitrate(-1));
  assert(!encoder.set_bitrate(800001));
  assert(encoder.driver.calls == calls);
  std::cout << "PASS: NVENC VBV scaling, rounding, defaults, validation and rollback\n";
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compiler', default='c++')
    parser.add_argument('--compiler-arg', action='append', default=[])
    parser.add_argument('--repo-root', type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='nvenc-bitrate-') as directory:
        source = Path(directory) / 'fixture.cpp'
        binary = Path(directory) / 'fixture.exe'
        source.write_text(fixture(args.repo_root))
        subprocess.run([args.compiler, *args.compiler_arg, '-std=c++20', '-UNDEBUG', '-Wall', '-Wextra', '-Werror',
                        str(source), '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True)


if __name__ == '__main__':
    main()
