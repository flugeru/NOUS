// Included in nvhttp.cpp next to the native pairing transaction.
namespace {
  std::mutex account_mutex;
  using account_clock = std::chrono::steady_clock;
  std::string activation;
  account_clock::time_point activation_expiry;
  std::unordered_map<std::string, account_clock::time_point> account_challenges;

  std::optional<nous_account::identity> account_identity(const std::string &token) {
    if (token.empty() || token.size() > 16384) return {};
    static nlohmann::json keys;
    static account_clock::time_point refresh, expiry;
    auto now = account_clock::now();
    if (now >= expiry) {
      if (now < refresh) return {};
      refresh = now + 30s;
      auto r = http::fetch_text("https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com", "GET", {}, {}, 32768, 5, false);
      if (!r || r->status != 200) return {};
      keys = nlohmann::json::parse(r->body, nullptr, false);
      if (!keys.is_object() || keys.empty()) return {};
      expiry = now + 5min;
    }
    return nous_account::verify(token, keys, now_seconds());
  }
}

nlohmann::json account_pairing(std::string_view operation, const nlohmann::json &body) {
  const nlohmann::json no = {{"status",false}};
  if (!body.is_object() || config::sunshine.flags[config::flag::FRESH_STATE] || !authorization_state_ready.load(std::memory_order_acquire)) return no;
  try {
    std::lock_guard lock(account_mutex);
    const auto now = account_clock::now();
    const auto path = config::nvhttp.pkey + ".nous-account.json";
    nlohmann::json state;
    const auto loaded = statefile::load_json(path,state);
    if (loaded != statefile::json_load_result_e::loaded && loaded != statefile::json_load_result_e::missing) return no;
    if (operation == "disable") {
      activation.clear(); account_challenges.clear();
      state["enabled"] = false;
      statefile::write_json_atomic(path,state);
      return {{"status",true}};
    }
    if (!config::sunshine.enable_pairing) return no;
    auto tls = crypto::x509(conf_intern.servercert);
    auto der = exact_certificate_identity(tls);
    if (!der) return no;
    const auto pin = util::hex(crypto::hash(*der),true).to_string();
    const auto host = boost::algorithm::to_lower_copy(http::unique_id);
    if (!pairing_policy::valid_paired_client_uuid(host)) return no;
    if (operation == "start") {
      activation = qr_random_hex(); activation_expiry = now + 3min;
      if (activation.empty()) return no;
      return {{"status",true},{"ticket",activation},{"pin",pin}};
    }
    if (operation == "enable") {
      const auto ticket = body.at("ticket").get<std::string>();
      if (activation.size()!=64 || now >= activation_expiry || ticket.size()!=64 || CRYPTO_memcmp(ticket.data(),activation.data(),64)) return no;
      activation.clear();
      auto id = account_identity(body.at("token").get<std::string>());
      if (!id || (state.value("enabled", false) && (state.at("uid") != id->uid || state.at("subject") != id->google_subject))) return no;
      // Keep the certificate ledger across disable/re-enable and owner changes. Missing record means revoked.
      if (!state.contains("issued")) state["issued"] = nlohmann::json::array();
      state["uid"]=id->uid; state["subject"]=id->google_subject; state["host"]=host; state["pin"]=pin; state["enabled"]=true;
      statefile::write_json_atomic(path,state); account_challenges.clear();
      return {{"status",true},{"host",host},{"name",config::nvhttp.sunshine_name},{"pin",pin}};
    }
    if (!state.value("enabled",false) || state.at("host")!=host || state.at("pin")!=pin || body.at("host")!=host) return no;
    auto pem=body.at("certificate").get<std::string>();
    if (pem.empty() || pem.size()>4096) return no;
    auto cert=crypto::x509(pem); auto identity=exact_certificate_identity(cert);
    if (!identity || !X509_get0_pubkey(cert.get())) return no;
    const auto cert_hash=util::hex(crypto::hash(*identity),true).to_string();
    const auto prefix="NOUS-ACCOUNT-1:" + host + ":" + pin + ":" + cert_hash + ":";
    std::erase_if(account_challenges,[&](const auto &e){return e.second<=now;});
    if (operation=="challenge") {
      if (account_challenges.size()>=64) return no;
      const auto nonce=qr_random_hex(); if (nonce.empty()) return no;
      account_challenges.emplace(prefix+nonce,now+90s);
      return {{"status",true},{"nonce",nonce}};
    }
    if (operation!="connect") return no;
    const auto nonce=body.at("nonce").get<std::string>(), sig=body.at("signature").get<std::string>();
    const auto proof=prefix+nonce;
    if (nonce.size()!=64 || !account_challenges.contains(proof) || !pairing_policy::valid_hex_field(sig,2,1024) || !crypto::verify256(cert,proof,util::from_hex_vec(sig,true))) return no;
    account_challenges.erase(proof);
    auto id=account_identity(body.at("token").get<std::string>());
    if (!id || state.at("uid")!=id->uid || state.at("subject")!=id->google_subject) return no;
    auto client=std::make_shared<crypto::named_cert_t>(); client->cert=crypto::pem(cert);
    client->name=body.at("name").get<std::string>();
    if (client->name.empty() || client->name.size()>96 || std::any_of(client->name.begin(),client->name.end(),[](unsigned char c){return c<32 || c==127;})) return no;
    client->uuid=uuid_util::uuid_t::generate().string(); client->perm=PERM::_default; client->allow_client_commands=false;
    auto &issued=state.at("issued"); if (!issued.is_array()) return no;
    bool known=std::find(issued.begin(),issued.end(),cert_hash)!=issued.end();
    if (!known) {
      if (issued.size()>=4096) return no;
      issued.push_back(cert_hash); statefile::write_json_atomic(path,state); // commit denial ledger first; fail closed on partial write
    }
    return {{"status",add_authorized_client(client,true,known)}};
  } catch (const std::exception &) { return no; }
}
