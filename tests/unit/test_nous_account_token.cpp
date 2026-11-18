// Standalone: c++ -std=c++20 tests/unit/test_nous_account_token.cpp -lcrypto -o account-test
#ifdef NDEBUG
#undef NDEBUG
#endif
#include "../../src/nous_account_token.h"
#include <cassert>
#include <ctime>
#include <openssl/rsa.h>

static std::string base64url(std::string_view in) {
  std::string out(4*((in.size()+2)/3)+1, '\0');
  out.resize(EVP_EncodeBlock(reinterpret_cast<unsigned char*>(out.data()), reinterpret_cast<const unsigned char*>(in.data()), in.size()));
  while (out.ends_with('=')) out.pop_back();
  std::replace(out.begin(), out.end(), '/', '_'); std::replace(out.begin(), out.end(), '+', '-');
  return out;
}
int main() {
  const auto now = std::time(nullptr);
  EVP_PKEY *key = EVP_RSA_gen(2048); assert(key);
  X509 *cert = X509_new(); assert(cert);
  X509_gmtime_adj(X509_getm_notBefore(cert), -120); X509_gmtime_adj(X509_getm_notAfter(cert), 3600);
  assert(X509_set_pubkey(cert,key)==1 && X509_sign(cert,key,EVP_sha256())>0);
  BIO *bio=BIO_new(BIO_s_mem()); assert(PEM_write_bio_X509(bio,cert)==1);
  char *p; auto n=BIO_get_mem_data(bio,&p);
  nlohmann::json keys={{"test",std::string(p,n)}};
  BIO_free(bio); X509_free(cert);
  nlohmann::json claims={{"iss","https://securetoken.google.com/nous-game-stream"},{"aud","nous-game-stream"},{"sub","owner"},{"iat",now},{"exp",now+3600},{"auth_time",now-3600},
    {"firebase",{{"sign_in_provider","google.com"},{"identities",{{"google.com",{"12345"}}}}}}};
  auto sign=[&](const nlohmann::json &c) {
    auto data=base64url(R"({"alg":"RS256","kid":"test"})")+"."+base64url(c.dump());
    EVP_MD_CTX *ctx=EVP_MD_CTX_new(); assert(EVP_DigestSignInit(ctx,nullptr,EVP_sha256(),nullptr,key)==1);
    size_t size=EVP_PKEY_size(key); std::string sig(size,'\0');
    assert(EVP_DigestSign(ctx,reinterpret_cast<unsigned char*>(sig.data()),&size,reinterpret_cast<const unsigned char*>(data.data()),data.size())==1);
    EVP_MD_CTX_free(ctx); sig.resize(size); return data+"."+base64url(sig);
  };
  const auto token=sign(claims);
  auto owner=nous_account::verify(token,keys,now); assert(owner && owner->uid=="owner" && owner->google_subject=="12345");
  assert(!nous_account::verify(token,keys,now+3600));
  assert(!nous_account::verify(token,{},now));
  auto tampered=token; auto i=tampered.rfind('.')+1; tampered[i]=tampered[i]=='A'?'B':'A'; assert(!nous_account::verify(tampered,keys,now));
  for(auto field:{"aud","iss","sub"}) {auto c=claims;c[field]="";assert(!nous_account::verify(sign(c),keys,now));}
  for(auto field:{"iat","auth_time"}) {auto c=claims;c[field]=now+1;assert(!nous_account::verify(sign(c),keys,now));}
  claims["firebase"]["sign_in_provider"]="password"; assert(!nous_account::verify(sign(claims),keys,now));
  EVP_PKEY_free(key);
}
