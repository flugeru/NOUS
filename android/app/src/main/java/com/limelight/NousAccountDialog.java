package com.limelight;

import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

/** Optional cloud identity. Never reads or writes local host pairing state. */
final class NousAccountDialog {
    private NousAccountDialog() {}

    static boolean isConfigured() {
        return !BuildConfig.ROOT_BUILD && "nous-game-stream".equals(BuildConfig.FIREBASE_PROJECT_ID)
                && !BuildConfig.FIREBASE_APP_ID.isEmpty() && !BuildConfig.FIREBASE_API_KEY.isEmpty()
                && !BuildConfig.FIREBASE_WEB_CLIENT_ID.isEmpty();
    }

    static void show(AppCompatActivity activity) {
        if (!isConfigured()) return;
        FirebaseApp app = FirebaseApp.getApps(activity).stream()
                .filter(candidate -> candidate.getName().equals("NousAccount"))
                .findFirst().orElse(null);
        if (app == null) {
            app = FirebaseApp.initializeApp(activity.getApplicationContext(), new FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(), "NousAccount");
        }
        FirebaseAuth auth = FirebaseAuth.getInstance(app);
        auth.setLanguageCode(activity.getResources().getConfiguration().getLocales().get(0).toLanguageTag());
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.nous_account)
                    .setMessage(activity.getString(R.string.nous_account_signed_in,
                            user.getEmail() == null ? activity.getString(R.string.nous_account) : user.getEmail()))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.nous_account_sign_out, (dialog, which) -> signOut(activity, auth))
                    .show();
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.nous_account)
                .setMessage(R.string.nous_account_optional)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.nous_account_google, null)
                .create();
        CancellationSignal signIn = new CancellationSignal();
        dialog.setOnDismissListener(ignored -> signIn.cancel());
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(new GetSignInWithGoogleOption.Builder(BuildConfig.FIREBASE_WEB_CLIENT_ID).build())
                    .build();
            CredentialManager.create(activity).getCredentialAsync(activity, request, signIn,
                    androidx.core.content.ContextCompat.getMainExecutor(activity), new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                @Override
                public void onResult(@NonNull GetCredentialResponse response) {
                    if (!dialog.isShowing() || activity.isDestroyed()) return;
                    if (!(response.getCredential() instanceof CustomCredential)
                            || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(response.getCredential().getType())) {
                        showSignInError(dialog);
                        return;
                    }
                    final String token;
                    try {
                        token = GoogleIdTokenCredential.createFrom(response.getCredential().getData()).getIdToken();
                    } catch (Exception invalidCredential) {
                        showSignInError(dialog);
                        return;
                    }
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                            .addOnCompleteListener(activity, task -> {
                                if (!dialog.isShowing() || activity.isDestroyed()) return;
                                if (task.isSuccessful()) {
                                    dialog.dismiss();
                                    show(activity);
                                } else {
                                    showSignInError(dialog);
                                }
                            });
                }

                @Override
                public void onError(@NonNull GetCredentialException error) {
                    if (!dialog.isShowing() || activity.isDestroyed()) return;
                    if (error instanceof GetCredentialCancellationException) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    } else {
                        showSignInError(dialog);
                    }
                }
            });
        });
    }

    private static void showSignInError(AlertDialog dialog) {
        dialog.setMessage(dialog.getContext().getString(R.string.nous_account_sign_in_failed));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
    }

    private static void signOut(AppCompatActivity activity, FirebaseAuth auth) {
        auth.signOut();
        CredentialManager.create(activity).clearCredentialStateAsync(new ClearCredentialStateRequest(), null,
                androidx.core.content.ContextCompat.getMainExecutor(activity), new CredentialManagerCallback<Void, ClearCredentialException>() {
            @Override public void onResult(Void unused) {}
            @Override public void onError(@NonNull ClearCredentialException error) {
                // Firebase session already ended. Account selector can still offer saved Google accounts.
            }
        });
    }
}
