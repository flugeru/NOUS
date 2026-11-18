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

    private static ArrayList<String> names(HttpUrl url, String token) throws Exception {
        ArrayList<String> names = new ArrayList<>();
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
                    JSONObject fields = docs.getJSONObject(i).getJSONObject("fields");
                    String name = fields.getJSONObject("name").getString("stringValue");
                    if (name.length() > 96 || name.isEmpty() || name.matches("(?s).*\\p{Cc}.*")) throw new IOException();
                    names.add(name);
                }
                String next = body.optString("nextPageToken");
                if (next.isEmpty()) break;
                if (next.length() > 4096 || pages.size() >= 15 || !pages.add(next)) throw new IOException();
                page = url.newBuilder().addQueryParameter("pageToken", next).build();
            }
        } while (true);
        return names;
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
                ArrayList<String> found = null;
                try { found = names(url, token); } catch (Exception ignored) { /* Never log tokens or directory contents. */ }
                final ArrayList<String> list = found;
                new Handler(Looper.getMainLooper()).post(() -> result(activity, auth, user, list));
            }, "nous-account-directory").start();
        });
    }

    private static void result(AppCompatActivity activity, FirebaseAuth auth, FirebaseUser user, ArrayList<String> list) {
        if (activity.isFinishing() || activity.isDestroyed() || auth.getCurrentUser() != user) return;
        ControllerDialogBuilder dialog = new ControllerDialogBuilder(activity);
        dialog.setTitle(R.string.nous_account_hosts);
        if (list == null || list.isEmpty()) dialog.setMessage(list == null ? R.string.nous_account_hosts_error : R.string.nous_account_hosts_empty);
        else dialog.setMessage(android.text.TextUtils.join("\n", list));
        dialog.setPositiveButton(android.R.string.ok, null).show();
    }
}
