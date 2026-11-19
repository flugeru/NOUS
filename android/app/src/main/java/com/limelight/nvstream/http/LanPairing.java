package com.limelight.nvstream.http;

import com.limelight.utils.DeviceUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** No credentials on discovery HTTP. Signed redemption uses the exact TLS pin scanned from Desktop. */
public final class LanPairing {
    private static final MediaType JSON = MediaType.get("application/json");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .callTimeout(8, TimeUnit.SECONDS).build();
    private final HttpUrl endpoint;
    private final X509Certificate previousPin;
    private final LimelightCryptoProvider crypto;
    private final String requestId;
    private final String certificateHash;

    private LanPairing(HttpUrl endpoint, X509Certificate previousPin, LimelightCryptoProvider crypto,
                       String requestId, String certificateHash) {
        this.endpoint = endpoint;
        this.previousPin = previousPin;
        this.crypto = crypto;
        this.requestId = requestId;
        this.certificateHash = certificateHash;
    }

    public static LanPairing begin(ComputerDetails.AddressTuple address, X509Certificate previousPin,
                                   LimelightCryptoProvider crypto) throws IOException, JSONException, GeneralSecurityException {
        byte[] pem = crypto.getPemEncodedClientCertificate();
        if (pem == null || crypto.getClientPrivateKey() == null) throw new IOException("Missing installation identity");
        HttpUrl entry = new HttpUrl.Builder().scheme("http").host(address.address).port(address.port)
                .addPathSegments("nous/lan-pairing").build();
        JSONObject reply = post(CLIENT, entry, new JSONObject().put("name", DeviceUtils.getModel())
                .put("certificate", new String(pem, StandardCharsets.US_ASCII)));
        String id = reply.optString("id");
        int port = reply.optInt("port");
        if (!reply.optBoolean("status") || !id.matches("[A-F0-9]{64}") || port < 1 || port > 65535) {
            throw new IOException("LAN pairing unavailable");
        }
        return new LanPairing(entry.newBuilder().scheme("https").port(port).build(), previousPin, crypto, id, sha256(pem));
    }

    public X509Certificate redeem(String payload, String expectedHostId) throws IOException, JSONException, GeneralSecurityException {
        String pin = scannedPin(payload, expectedHostId, requestId, certificateHash);
        if (pin == null || (previousPin != null && !pin.equals(sha256(previousPin.getEncoded())))) {
            throw new IOException("QR does not match this host and request");
        }
        Signature proof = Signature.getInstance("EC".equals(crypto.getClientPrivateKey().getAlgorithm())
                ? "SHA256withECDSA" : "SHA256withRSA");
        proof.initSign(crypto.getClientPrivateKey());
        proof.update(payload.getBytes(StandardCharsets.US_ASCII));
        X509Certificate[] peer = new X509Certificate[1];
        OkHttpClient client = pinned(endpoint, pin, peer);
        JSONObject reply = post(client, endpoint, new JSONObject().put("payload", payload).put("signature", hex(proof.sign())));
        if (!reply.optBoolean("status") || peer[0] == null) throw new IOException("LAN pairing rejected");
        return peer[0];
    }

    public static X509Certificate connectAccount(ComputerDetails.AddressTuple address, String host, String pin, String token,
                                                 LimelightCryptoProvider crypto) throws IOException, JSONException, GeneralSecurityException {
        if (address == null || address.port >= 65535 || !host.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}") ||
                !pin.matches("[A-F0-9]{64}") || token == null || token.isEmpty()) throw new IOException("Invalid account endpoint");
        HttpUrl endpoint = new HttpUrl.Builder().scheme("https").host(address.address).port(address.port + 1).addPathSegments("nous/account/challenge").build();
        X509Certificate[] peer = new X509Certificate[1];
        var client = pinned(endpoint, pin, peer);
        JSONObject body = new JSONObject().put("host", host).put("certificate", new String(crypto.getPemEncodedClientCertificate(), StandardCharsets.US_ASCII));
        var challenge = post(client, endpoint, body);
        String nonce = challenge.optString("nonce");
        if (!challenge.optBoolean("status") || !nonce.matches("[A-F0-9]{64}") || peer[0] == null) throw new IOException("Account access unavailable");
        String payload = "NOUS-ACCOUNT-1:" + host + ":" + pin + ":" + sha256(crypto.getClientCertificate().getEncoded()) + ":" + nonce;
        Signature proof = Signature.getInstance("EC".equals(crypto.getClientPrivateKey().getAlgorithm()) ? "SHA256withECDSA" : "SHA256withRSA");
        proof.initSign(crypto.getClientPrivateKey()); proof.update(payload.getBytes(StandardCharsets.US_ASCII));
        var result = post(client, endpoint.newBuilder().setPathSegment(2,"connect").build(), body.put("nonce",nonce).put("token",token)
                .put("name", DeviceUtils.getModel()).put("signature",hex(proof.sign())));
        if (!result.optBoolean("status")) throw new IOException("Account access rejected");
        return peer[0];
    }

    private static OkHttpClient pinned(HttpUrl endpoint, String pin, X509Certificate[] peer) throws GeneralSecurityException {
        X509TrustManager trust = new X509TrustManager() {
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            @Override public void checkClientTrusted(X509Certificate[] chain, String type) throws CertificateException {
                throw new CertificateException("Server certificate required");
            }
            @Override public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException {
                if (chain.length == 0) throw new CertificateException("Missing host certificate");
                try {
                    chain[0].checkValidity();
                    if (!pin.equals(sha256(chain[0].getEncoded()))) throw new CertificateException("Host pin mismatch");
                } catch (GeneralSecurityException err) {
                    throw new CertificateException("Host certificate rejected", err);
                }
                peer[0] = chain[0];
            }
        };
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(null, new X509TrustManager[]{trust}, new SecureRandom());
        return CLIENT.newBuilder().sslSocketFactory(tls.getSocketFactory(), trust)
                .hostnameVerifier((host, session) -> endpoint.host().equals(host) && peer[0] != null).build();
    }

    static String scannedPin(String payload, String hostId, String id, String certificateHash) {
        if (payload == null || payload.length() != 307 || !payload.matches(
                "NOUS-LAN-1:[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}:(?:[A-F0-9]{64}:){3}[A-F0-9]{64}")) return null;
        String[] fields = payload.split(":");
        return fields[1].equals(hostId) && fields[2].equals(id) && fields[4].equals(certificateHash) ? fields[3] : null;
    }

    private static JSONObject post(OkHttpClient client, HttpUrl url, JSONObject params) throws IOException, JSONException {
        Request request = new Request.Builder().url(url).post(RequestBody.create(params.toString(), JSON)).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("LAN pairing unavailable");
            if (response.peekBody(4097).contentLength() > 4096) throw new IOException("Invalid LAN pairing reply");
            return new JSONObject(response.peekBody(4096).string());
        }
    }

    private static String sha256(byte[] value) throws GeneralSecurityException {
        return hex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String hex(byte[] value) {
        char[] result = new char[value.length * 2];
        String digits = "0123456789ABCDEF";
        for (int i = 0; i < value.length; i++) {
            result[2 * i] = digits.charAt((value[i] & 255) >> 4);
            result[2 * i + 1] = digits.charAt(value[i] & 15);
        }
        return new String(result);
    }
}
