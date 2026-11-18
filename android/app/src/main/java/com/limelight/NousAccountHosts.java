package com.limelight;

import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.limelight.ui.ControllerDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Reads discovery metadata from Google's endpoint only. Never grants access or replaces a saved pin. */
final class NousAccountHosts {
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().followRedirects(false)
            .followSslRedirects(false).callTimeout(8, TimeUnit.SECONDS).build();
    private static final String ROOT = "projects/nous-game-stream/databases/(default)/documents/";

    static HttpUrl directory(String uid, String subject) {
        if (uid == null || !uid.matches("[A-Za-z0-9_-]{1,128}") || subject == null || !subject.matches("[0-9]{1,128}"))
            throw new IllegalArgumentException("Invalid identity");
        return new HttpUrl.Builder().scheme("https").host("firestore.googleapis.com").addPathSegment("v1")
                .addPathSegments(ROOT + "accounts").addPathSegment(uid).addPathSegment("google")
                .addPathSegment(subject).addPathSegment("hosts").addQueryParameter("pageSize", "64").build();
    }

    static final class Host {
        final String uuid, name, pin;
        Host(String document, String prefix, String name, String pin) throws IOException {
            if (!document.startsWith(prefix)) throw new IOException();
            this.uuid = document.substring(prefix.length());
            if (!uuid.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}") || !pin.matches("[A-F0-9]{64}") ||
                    name.isEmpty() || name.length() > 96 || name.matches("(?s).*[\\p{Cc}\\p{Cf}].*")) throw new IOException();
            this.name = name;
            this.pin = pin;
        }

        boolean matchesSavedCertificate(byte[] der) throws java.security.GeneralSecurityException {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(der);
            return pin.equals(okio.ByteString.of(hash).hex().toUpperCase(java.util.Locale.ROOT));
        }
    }

    private static ArrayList<Host> hosts(HttpUrl url, String token) throws Exception {
        ArrayList<Host> hosts = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        String prefix = url.encodedPath().substring("/v1/".length()) + "/";
        HashSet<String> pages = new HashSet<>();
        HttpUrl page = url;
        do {
            Request req = new Request.Builder().url(page).header("Authorization", "Bearer " + token).build();
            try (Response response = CLIENT.newCall(req).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new IOException();
                byte[] bytes = response.peekBody(131073).bytes();
                if (bytes.length > 131072) throw new IOException();
                JSONObject body = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                JSONArray docs = body.has("documents") ? body.getJSONArray("documents") : new JSONArray();
                for (int i = 0; i < docs.length(); i++) {
                    JSONObject doc = docs.getJSONObject(i), fields = doc.getJSONObject("fields");
                    Host host = new Host(doc.getString("name"), prefix, fields.getJSONObject("name").getString("stringValue"),
                            fields.getJSONObject("pin").getString("stringValue"));
                    if (!ids.add(host.uuid) || hosts.size() >= 1024) throw new IOException();
                    hosts.add(host);
                }
                String next = body.optString("nextPageToken");
                if (next.isEmpty()) break;
                if (next.length() > 4096 || pages.size() >= 15 || !pages.add(next)) throw new IOException();
                page = url.newBuilder().addQueryParameter("pageToken", next).build();
            }
        } while (true);
        return hosts;
    }

    static void show(AppCompatActivity activity, FirebaseAuth auth, FirebaseUser user) {
        String subject = null;
        for (UserInfo info : user.getProviderData()) if ("google.com".equals(info.getProviderId())) subject = info.getUid();
        final HttpUrl url;
        try { url = directory(user.getUid(), subject); }
        catch (IllegalArgumentException e) { result(activity, auth, user, null); return; }
        user.getIdToken(false).addOnCompleteListener(activity, task -> {
            if (!task.isSuccessful() || task.getResult().getToken() == null) { result(activity, auth, user, null); return; }
            String token = task.getResult().getToken();
            new Thread(() -> {
                ArrayList<Host> found = null;
                try { found = hosts(url, token); } catch (Exception ignored) { /* Never log tokens or directory contents. */ }
                final ArrayList<Host> list = found;
                new Handler(Looper.getMainLooper()).post(() -> result(activity, auth, user, list));
            }, "nous-account-directory").start();
        });
    }

    private static void result(AppCompatActivity activity, FirebaseAuth auth, FirebaseUser user, ArrayList<Host> list) {
        if (activity.isFinishing() || activity.isDestroyed() || auth.getCurrentUser() != user) return;
        ControllerDialogBuilder dialog = new ControllerDialogBuilder(activity);
        dialog.setTitle(R.string.nous_account_hosts);
        if (list == null || list.isEmpty()) dialog.setMessage(list == null ? R.string.nous_account_hosts_error : R.string.nous_account_hosts_empty);
        else {
            String[] labels = new String[list.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = list.get(i).name + " · " + list.get(i).uuid.substring(0, 8);
            dialog.setItems(labels, (ignored, i) -> {
                if (auth.getCurrentUser() == user && activity instanceof PcView && !activity.isDestroyed())
                    ((PcView) activity).openPairedAccountHost(list.get(i));
            });
        }
        dialog.setPositiveButton(android.R.string.ok, null).show();
    }
}
