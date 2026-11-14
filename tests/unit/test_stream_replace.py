"""CPU regression for the actual transport replacement helper, without host dependencies.

The function body is extracted from stream.cpp; this does not compile its full TU.
Run: python tests/unit/test_stream_replace.py path/to/cxx
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("compiler")
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
source = (root / "src/stream.cpp").read_text(encoding="utf-8")
start = source.index("  std::vector<uint8_t> replace(")
end = source.index("\n  }", start) + len("\n  }")
program = """
#include <algorithm>
#include <cassert>
#include <cstdint>
#include <iterator>
#include <string>
#include <string_view>
#include <vector>
""" + source[start:end] + r'''
int main() {
  const auto check = [](std::string_view input, std::string_view old,
                        std::string_view replacement, std::string_view expected) {
    const auto result = replace(input, old, replacement);
    assert(std::string(result.begin(), result.end()) == expected);
  };
  check("x", "header absent", "y", "x");
  check("", "header absent", "y", "");
  check("abc", "not here", "", "abc");
  check("abcabc", "abc", "z", "zabc");
  check("abc", "b", "long", "alongc");
  check("abc", "b", "", "ac");
  check("abc", "", "+", "+abc");
}
'''
with tempfile.TemporaryDirectory(prefix="stream-replace-") as temporary:
    cpp = Path(temporary) / "stream_replace.cpp"
    exe = Path(temporary) / "stream_replace.exe"
    cpp.write_text(program, encoding="utf-8")
    subprocess.run([args.compiler, "-std=c++23", "-O0", "-UNDEBUG", str(cpp), "-o", str(exe)], check=True)
    subprocess.run([str(exe)], check=True)
print("PASS: 7 transport replacement cases (production function body)")
