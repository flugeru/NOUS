#pragma once

#include <algorithm>
#include <chrono>
#include <ctime>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <nlohmann/json.hpp>
#include <openssl/evp.h>
#include <openssl/pem.h>

namespace nous_account {
  struct identity { std::string uid, google_subject; };

  namespace detail {
    inline std::optional<std::string> decode(std::string_view value) {
      if (value.empty() || value.size() > 16384 || value.size() % 4 == 1) return {};
      std::string s(value);
      for (char &c : s) {
        if (c == '-') c = '+';
        else if (c == '_') c = '/';
        else if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9')) return {};
      }
      const auto pad = (4 - s.size() % 4) % 4;
      s.append(pad, '=');
      std::string out(s.size(), '\0');
      const int len = EVP_DecodeBlock(reinterpret_cast<unsigned char *>(out.data()), reinterpret_cast<const unsigned char *>(s.data()), static_cast<int>(s.size()));
      if (len < static_cast<int>(pad)) return {};
      out.resize(len - pad);
      return out;
    }
  }

  // keys must come exclusively from Google's securetoken X509 endpoint over verified TLS.
  inline std::optional<identity> verify(std::string_view token, const nlohmann::json &keys, int64_t now) {
    if (token.size() > 16384) return {};
    const auto p = token.find('.');
    if (p == token.npos) return {};
    const auto q = token.find('.', p + 1);
    if (q == token.npos) return {};
    const auto header = detail::decode(token.substr(0, p));
    const auto sig = detail::decode(token.substr(q + 1));
    if (!header || !sig || !keys.is_object()) return {};
    try {
      const auto h = nlohmann::json::parse(*header);
      if (h.at("alg") != "RS256" || h.contains("crit")) return {};
      const auto &pem = keys.at(h.at("kid").get<std::string>()).get_ref<const std::string &>();
      std::unique_ptr<BIO, decltype(&BIO_free)> buf(BIO_new_mem_buf(pem.data(), static_cast<int>(pem.size())), BIO_free);
      if (!buf) return {};
      std::unique_ptr<X509, decltype(&X509_free)> cert(PEM_read_bio_X509(buf.get(), nullptr, nullptr, nullptr), X509_free);
      const auto certificate_now = std::time(nullptr);
      if (!cert || ASN1_TIME_cmp_time_t(X509_get0_notBefore(cert.get()), certificate_now) != -1 || ASN1_TIME_cmp_time_t(X509_get0_notAfter(cert.get()), certificate_now) != 1) return {};
      auto *key = X509_get0_pubkey(cert.get());
      std::unique_ptr<EVP_MD_CTX, decltype(&EVP_MD_CTX_free)> ctx(EVP_MD_CTX_new(), EVP_MD_CTX_free);
      if (!ctx || !key || EVP_PKEY_base_id(key) != EVP_PKEY_RSA || EVP_PKEY_bits(key) < 2048 ||
          EVP_DigestVerifyInit(ctx.get(), nullptr, EVP_sha256(), nullptr, key) != 1 ||
          EVP_DigestVerify(ctx.get(), reinterpret_cast<const unsigned char *>(sig->data()), sig->size(), reinterpret_cast<const unsigned char *>(token.data()), q) != 1) return {};
      auto payload = detail::decode(token.substr(p + 1, q - p - 1));
      if (!payload) return {};
      const auto c = nlohmann::json::parse(*payload);
      if (c.at("aud") != "nous-game-stream" || c.at("iss") != "https://securetoken.google.com/nous-game-stream" ||
          !c.at("exp").is_number_integer() || !c.at("iat").is_number_integer() || !c.at("auth_time").is_number_integer()) return {};
      auto exp = c.at("exp").get<int64_t>(), iat = c.at("iat").get<int64_t>(), auth = c.at("auth_time").get<int64_t>();
      if (auth <= 0 || auth > iat || iat > now || now >= exp || exp - iat > 3600) return {};
      const auto &f = c.at("firebase");
      const auto &g = f.at("identities").at("google.com");
      if (f.at("sign_in_provider") != "google.com" || f.contains("tenant") || !g.is_array() || g.size() != 1) return {};
      identity id {c.at("sub").get<std::string>(), g.at(0).get<std::string>()};
      if (id.uid.empty() || id.uid.size() > 128 || id.uid.find_first_not_of("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-") != id.uid.npos ||
          id.google_subject.empty() || id.google_subject.size() > 128 || id.google_subject.find_first_not_of("0123456789") != id.google_subject.npos) return {};
      return id;
    } catch (const nlohmann::json::exception &) { return {}; }
  }
}
