"""Compile changed certificate/signature code against installed OpenSSL; no trust changes."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / 'src/crypto.cpp').read_text()
start = source.index('  std::string_view signature(const X509 *x) {')
end = source.index('\n  std::string_view signature(const x509_t &x)', start)
signature = source[start:end]
start = source.index('    util::safe_ptr<X509_NAME, X509_NAME_free> name')
end = source.index('\n    X509_sign(', start)
subject = source[start:end]
program = r'''
#include <openssl/pem.h>
#include <cassert>
#include <cstdint>
#include <memory>
#include <string_view>
namespace util {
template<class T, auto Free> struct deleter {
  void operator()(T *value) const { Free(value); }
};
template<class T, auto Free> using safe_ptr = std::unique_ptr<T, deleter<T, Free>>;
}
''' + signature + r'''
bool set_subject(X509 *certificate, std::string_view cn) {
  auto x509 = std::unique_ptr<X509, decltype(&X509_free)>(certificate, X509_free);
''' + subject + r'''
  x509.release();
  return true;
}
int main() {
  auto cert = std::unique_ptr<X509, decltype(&X509_free)>(X509_new(), X509_free);
  assert(cert);
  assert(signature(cert.get()).empty());
  assert(set_subject(cert.get(), "NOUS API regression"));
  char name[80] = {};
  assert(X509_NAME_get_text_by_NID(X509_get_subject_name(cert.get()), NID_commonName, name, sizeof(name)) > 0);
  assert(std::string_view(name) == "NOUS API regression");
  assert(X509_NAME_cmp(X509_get_subject_name(cert.get()), X509_get_issuer_name(cert.get())) == 0);
  auto ctx = std::unique_ptr<EVP_PKEY_CTX, decltype(&EVP_PKEY_CTX_free)>(EVP_PKEY_CTX_new_id(EVP_PKEY_RSA, nullptr), EVP_PKEY_CTX_free);
  assert(ctx && EVP_PKEY_keygen_init(ctx.get()) == 1);
  assert(EVP_PKEY_CTX_set_rsa_keygen_bits(ctx.get(), 2048) == 1);
  EVP_PKEY *raw = nullptr;
  assert(EVP_PKEY_keygen(ctx.get(), &raw) == 1);
  auto key = std::unique_ptr<EVP_PKEY, decltype(&EVP_PKEY_free)>(raw, EVP_PKEY_free);
  assert(X509_set_pubkey(cert.get(), key.get()) == 1);
  assert(X509_sign(cert.get(), key.get(), EVP_sha256()) > 0);
  assert(!signature(cert.get()).empty());
  assert(X509_verify(cert.get(), key.get()) == 1);
}
'''
with tempfile.TemporaryDirectory(prefix='nous-crypto-') as folder:
    path = Path(folder)
    (path / 'check.cpp').write_text(program)
    binary = path / ('check.exe' if os.name == 'nt' else 'check')
    subprocess.run(['c++', '-std=c++17', str(path / 'check.cpp'), '-o', str(binary), '-lcrypto'], check=True)
    subprocess.run([str(binary)], check=True)
print('PASS: certificate subject/issuer, empty signature, signed signature and verification')
