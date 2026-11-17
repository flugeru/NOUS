// Run from repo root (MSYS2 UCRT64):
// c++ -std=c++20 -I. tests/session_history_stutter_check.cpp src/session_history_storage.cpp src/session_history_storage_diagnostics.cpp -lsqlite3 -ladvapi32 -o build-local/history-stutter-check.exe && build-local/history-stutter-check.exe
#include "src/session_history_storage.h"
#include <cassert>

int main() {
  using namespace session_history;
  storage::db_ptr db;
  assert(storage::open_memory_db(db));
  assert(storage::apply_schema_and_migrations(db.get(), 7));
  for (const bool slow : {false, true}) {
    session_metadata_t meta;
    meta.uuid = slow ? "slow" : "startup";
    meta.target_fps = 60;
    assert(storage::process_begin_at(db.get(), meta, 10));
    for (int i = 0; i < 10; ++i) {
      session_sample_t sample;
      sample.session_uuid = meta.uuid;
      sample.timestamp_unix = 12 + i * 2;
      sample.frames_sent = 100 + i * 100;
      sample.actual_fps = i < 2 ? 0 : slow ? 20 : 60;
      assert(storage::process_sample(db.get(), sample, 100));
    }
    assert(storage::process_end_at(db.get(), meta.uuid, 33));
    auto detail = storage::read_session_detail(db.get(), meta.uuid, true, 100, 100);
    assert(detail);
    assert(detail->summary.verdict == (slow ? "degraded" : "healthy"));
  }
}
