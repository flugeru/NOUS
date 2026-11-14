"""Compile literal RTSP admission and control-handler snippets in CPU fixtures.

Does not compile full RTSP/stream translation units or open sockets.
Run: python tests/unit/test_transport_boundaries.py path/to/cxx
"""
import argparse
from pathlib import Path
import re
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("compiler")
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
rtsp = (root / "src/rtsp.cpp").read_text(encoding="utf-8")
stream = (root / "src/stream.cpp").read_text(encoding="utf-8")
config_header = (root / "src/config.h").read_text(encoding="utf-8")
constants = "\n".join(re.findall(r"constexpr int PACKETSIZE_\w+ = \d+;", config_header))
start = rtsp.index('      const auto requested_packet_size = args.at(')
end = rtsp.index('\n      config.minRequiredFecPackets', start)
admission = rtsp[start:end]
assert rtsp.index('stream_session = stream::session::alloc(config, *launch_session);') > end
# The actual field parser must be guarded too: an empty SDP value precedes admission.
trim_start = rtsp.index('        if (!val.empty() && val.back()')
trim_end = rtsp.index('\n        args.emplace(name, val);', trim_start)
trim = rtsp[trim_start:trim_end]
handlers = []
for name in ("IDX_SET_CLIPBOARD", "IDX_FILE_TRANSFER_NONCE_REQUEST"):
    begin = stream.index('    server->map(packetTypes[' + name + ']')
    begin = stream.index('{\n', begin) + 2
    end = stream.index('\n    });', begin)
    handlers.append('void ' + name + '(session_t *session, const std::string_view &payload) {\n' + stream[begin:end] + '\n}\n')
program = r'''
#include <cassert>
#include <charconv>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>
using namespace std::literals;
namespace config {
''' + constants + r'''
  struct { int packetsize = 0; int pacing_max_bitrate_kbps = 0; } stream;
}
std::vector<std::string> logs;
struct logger {
  std::ostringstream text;
  template<class T> logger &operator<<(const T &value) { text << value; return *this; }
  ~logger() { logs.push_back(text.str()); }
};
#define BOOST_LOG(level) logger {}
int response_code = 0;
struct session_t { int permission = 0; std::string device_name = "test-device"; };
namespace crypto::PERM { constexpr int clipboard_set = 1; constexpr int file_upload = 2; }
struct { int sock = 0; } socket_fixture;
struct { int sequenceNumber = 1; } request_fixture;
void respond(int, session_t &, int *, int status, const char *, int, std::initializer_list<int>) {
  response_code = status;
}
int negotiate(const std::unordered_map<std::string_view, std::string_view> &args) {
  struct { int packetsize = 0; } config;
  auto *socket = &socket_fixture;
  auto *req = &request_fixture;
  session_t session_fixture;
  auto *session = &session_fixture;
  int option = 0;
  try {
''' + admission + r'''
  } catch (const std::out_of_range &) {
    response_code = 400;
    return false;
  }
  return config.packetsize;
}
std::string_view trim_value(std::string_view val) {
''' + trim + r'''
  return val;
}
''' + '\n'.join(handlers) + r'''
int main() {
  assert(config::PACKETSIZE_MIN == 200 && config::PACKETSIZE_MAX == 65535);
  assert(trim_value("").empty());
  assert(trim_value(" ").empty());
  assert(trim_value("200 ") == "200");
  int cases = 0;
  for (int ceiling : {0, 200000}) {
    config::stream.pacing_max_bitrate_kbps = ceiling;
    for (int override_size : {0, 199, 200, 1024, 1408, 65535, 65536, -1}) {
      config::stream.packetsize = override_size;
      for (auto text : {"-2147483649", "-1", "0", "15", "16", "17", "199", "65536",
                        "2147483648", "4294967496", "9999999999999999999999999999",
                        "", " ", "+200", "200x", "200  ", "1e3"}) {
        response_code = 0;
        assert(negotiate({{"x-nv-video[0].packetSize", text}}) == 0);
        assert(response_code == 400);
        ++cases;
      }
      response_code = 0;
      assert(negotiate({}) == 0 && response_code == 400);
      ++cases;
      for (auto text : {"200", "201", "500", "1024", "1376", "1408", "65534", "65535", "00200"}) {
        int requested = std::stoi(text);
        int expected = requested;
        if (override_size >= config::PACKETSIZE_MIN && override_size <= config::PACKETSIZE_MAX && override_size < requested) {
          expected = override_size;
        }
        response_code = 0;
        assert(negotiate({{"x-nv-video[0].packetSize", text}}) == expected);
        assert(response_code == 0);
        ++cases;
      }
    }
  }
  int control_cases = 0;
  for (auto handler : {IDX_SET_CLIPBOARD, IDX_FILE_TRANSFER_NONCE_REQUEST}) {
    for (int permission : {0, 1, 2, 3}) {
      for (auto payload : {"", "private-clipboard-text", "private-nonce\nsecond-line"}) {
        session_t session {permission, "test-device"};
        logs.clear();
        handler(&session, payload);
        assert(logs.size() == 1);
        const bool allowed = permission & (handler == IDX_SET_CLIPBOARD ? crypto::PERM::clipboard_set : crypto::PERM::file_upload);
        if (allowed) {
          const auto label = handler == IDX_SET_CLIPBOARD ? "type [IDX_SET_CLIPBOARD] size: " : "type [IDX_FILE_TRANSFER_NONCE_REQUEST] size: ";
          assert(logs[0] == label + std::to_string(std::string_view(payload).size()));
        } else {
          assert(logs[0].find("size:") == std::string::npos);
          assert(logs[0].find("Permission ") == 0);
        }
        assert(logs[0].find("private-") == std::string::npos);
        ++control_cases;
      }
    }
  }
  std::cout << "PASS: " << cases << " RTSP boundary cases; " << control_cases
            << " control privacy cases; 3 SDP trim cases (production snippets)\n";
}
'''
with tempfile.TemporaryDirectory(prefix="transport-boundaries-") as temporary:
    cpp = Path(temporary) / "transport_boundaries.cpp"
    exe = Path(temporary) / "transport_boundaries.exe"
    cpp.write_text(program, encoding="utf-8")
    subprocess.run([args.compiler, "-std=c++23", "-O0", "-UNDEBUG", str(cpp), "-o", str(exe)], check=True)
    subprocess.run([str(exe)], check=True)
