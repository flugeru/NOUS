/**
 * @file src/httpcommon.h
 * @brief Declarations for common HTTP.
 */
#pragma once

// standard includes
#include <cstddef>
#include <optional>
#include <string>

// lib includes
#include <curl/curl.h>

// local includes
#include "network.h"
#include "http_policy.h"
#include "thread_safe.h"
#include "uuid.h"

namespace http {

  using creds_state = policy::creds_state;

  int init();
  int create_creds(const std::string &pkey, const std::string &cert);
  int save_user_creds(
    const std::string &file,
    const std::string &username,
    const std::string &password,
    bool run_our_mouth = false
  );

  creds_state user_creds_state(const std::string &file);
  int reload_user_creds(const std::string &file);
  bool download_file(const std::string &url, const std::string &file, long ssl_version = CURL_SSLVERSION_TLSv1_2);

  struct text_response_t {
    long status {0};
    std::string body;
  };

  /**
   * @brief Fetch a small HTTPS text response with strict bounds.
   *
   * Only HTTPS is permitted. Redirects are limited, the response body is
   * capped, and no credentials or client headers are forwarded. Returns
   * nullopt when the transfer fails or the body exceeds max_bytes.
   */
  std::optional<text_response_t> fetch_text(
    const std::string &url,
    const std::string &method = "GET",
    const std::string &body = {},
    const std::string &content_type = {},
    std::size_t max_bytes = 2 * 1024 * 1024,
    long timeout_seconds = 15,
    bool follow_redirects = true
  );

  bool configure_curl_tls(CURL *curl);
  std::string url_escape(const std::string &url);
  std::string url_get_host(const std::string &url);
  std::string cookie_escape(const std::string &value);
  std::string cookie_unescape(const std::string &value);

  extern std::string unique_id;
  extern uuid_util::uuid_t uuid;
  // Set only when this process had to create both host credential files. It
  // lets state startup distinguish first-run initialization from an existing
  // profile whose pairing snapshots have disappeared.
  extern bool credentials_created_this_run;
  extern net::net_e origin_web_ui_allowed;

#ifdef _WIN32
  std::string shared_virtual_display_guid_snapshot();
  // False preserves the existing owner while exact borrowers/probes drain.
  bool set_shared_virtual_display_guid(std::string value);
#endif

  // Update origin ACL from current config
  void refresh_origin_acl();

}  // namespace http
