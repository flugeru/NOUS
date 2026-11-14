"""Run production keyed-mutex acquisition/cleanup branches against CPU fakes.

No Windows SDK or GPU calls. Extract guards verbatim; counters stand at the
first downstream read/copy boundary. Baseline must not pass abandoned surfaces.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile


def fixture(root):
    wgc = (root / 'src/platform/windows/display_wgc.cpp').read_text()
    wgc_start = wgc.index('    const auto capture_mutex_wait_start =')
    wgc_guard = wgc[wgc_start:wgc.index('    texture2d_t src;', wgc_start)]
    vram = (root / 'src/platform/windows/display_vram.cpp').read_text()
    vram_start = vram.index('      auto status = img_ctx.encoder_mutex->AcquireSync(0, 3000);')
    vram_guard = vram[vram_start:vram.index('      // An image that aliases', vram_start)]
    helper_start = vram.index('    ~texture_lock_helper() {')
    helper_methods = vram[helper_start:vram.index('\n  };', helper_start)]
    return r'''
#include <cassert>
#include <chrono>
#include <cstdint>
#include <iostream>
#include <sstream>
#include <string_view>
#include <utility>
using namespace std::literals;
using HRESULT = std::int32_t;
constexpr HRESULT S_OK = 0, WAIT_ABANDONED = 128, WAIT_TIMEOUT = 258, E_FAIL = -1;
bool FAILED(HRESULT value) { return value < 0; }
#define BOOST_LOG(level) std::ostringstream{}
namespace util {
  struct hex {
    explicit hex(HRESULT) {}
    const char *to_string_view() const { return "fake"; }
  };
  template<class F> struct fail_guard {
    F fn;
    explicit fail_guard(F fn): fn(std::move(fn)) {}
    ~fail_guard() { fn(); }
  };
}
struct Mutex {
  HRESULT result;
  unsigned acquisitions = 0, releases = 0;
  HRESULT AcquireSync(unsigned key, unsigned timeout) {
    assert(key == 0 && timeout == 3000);
    ++acquisitions;
    return result;
  }
  HRESULT ReleaseSync(unsigned key) {
    assert(key == 0);
    ++releases;
    return S_OK;
  }
};
struct texture_lock_helper {
  Mutex *_mutex;
  bool _locked = false;
''' + helper_methods + r'''
};
struct Edges { unsigned reads = 0, copies = 0; };
enum class capture_e { ok, error, reinit };
capture_e capture(Mutex &mutex, Edges &edges) {
  struct Image { Mutex *capture_mutex; } image{&mutex};
  auto *d3d_img = &image;
''' + wgc_guard + r'''
  (void)capture_mutex_wait;
  // IPC read and GPU copy boundaries reached only after valid acquisition.
  ++edges.reads;
  ++edges.copies;
  return capture_e::ok;
}
int convert(Mutex &mutex, Edges &edges) {
  struct ImageContext { Mutex *encoder_mutex; } img_ctx{&mutex};
''' + vram_guard + r'''
  // Shared metadata read and shader sampling boundaries.
  ++edges.reads;
  ++edges.copies;
  return 0;
}
int main() {
  for (HRESULT result : {S_OK, WAIT_ABANDONED, WAIT_TIMEOUT, E_FAIL}) {
    Mutex capture_mutex{result};
    Edges capture_edges;
    const auto captured = capture(capture_mutex, capture_edges);
    assert(capture_mutex.acquisitions == 1);
    assert(captured == (result == S_OK ? capture_e::ok :
                       result == WAIT_ABANDONED ? capture_e::reinit : capture_e::error));
    assert(capture_mutex.releases == (result == S_OK ? 1u : 0u));
    assert(capture_edges.reads == (result == S_OK ? 1u : 0u));
    assert(capture_edges.copies == (result == S_OK ? 1u : 0u));
    Mutex encoder_mutex{result};
    Edges encoder_edges;
    const auto converted = convert(encoder_mutex, encoder_edges);
    assert(encoder_mutex.acquisitions == 1);
    assert(converted == (result == S_OK ? 0 : -1));
    assert(encoder_mutex.releases == (result == S_OK ? 1u : 0u));
    assert(encoder_edges.reads == (result == S_OK ? 1u : 0u));
    assert(encoder_edges.copies == (result == S_OK ? 1u : 0u));
    Mutex helper_mutex{result};
    {
      texture_lock_helper helper{&helper_mutex};
      assert(helper.lock() == (result == S_OK));
      assert(helper._locked == (result == S_OK));
      assert(helper_mutex.acquisitions == 1);
      assert(helper_mutex.releases == 0);
      if (result == S_OK) {
        assert(helper.lock());
        assert(helper_mutex.acquisitions == 1);
      }
    }
    assert(helper_mutex.releases == (result == S_OK ? 1u : 0u));
  }
  std::cout << "PASS: capture/encoder mutex success, abandonment, timeout and failure; no invalid reads, copies or releases\n";
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--compiler', default='c++')
    parser.add_argument('--compiler-arg', action='append', default=[])
    parser.add_argument('--repo-root', type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='keyed-mutex-abandoned-') as directory:
        source = Path(directory) / 'fixture.cpp'
        binary = Path(directory) / 'fixture.exe'
        source.write_text(fixture(args.repo_root))
        subprocess.run([args.compiler, *args.compiler_arg, '-std=c++20', '-UNDEBUG',
                        '-Wall', '-Wextra', '-Werror', str(source), '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True)


if __name__ == '__main__':
    main()
