package com.proxy.wireopen.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * OpenVPN credentials. Saved credentials persist; temporary credentials live only
 * until the current VPN session ends.
 */
public class CredentialManager {
    private static final String PREF_NAME = "openvpn_credentials";
    private static final String KEY_USERNAME = "ovpn_username";
    private static final String KEY_PASSWORD = "ovpn_password";

    private static CredentialManager instance;
    private final SharedPreferences prefs;
    private String temporaryUsername = "";
    private String temporaryPassword = "";

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new CredentialManager(context.getApplicationContext());
        }
    }

    public static synchronized CredentialManager getInstance() {
        return instance;
    }

    private CredentialManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void saveCredentials(String username, String password) {
        prefs.edit()
                .putString(KEY_USERNAME, username != null ? username.trim() : "")
                .putString(KEY_PASSWORD, password != null ? password : "")
                .apply();
    }

    public synchronized void setTemporaryCredentials(String username, String password) {
        temporaryUsername = username != null ? username.trim() : "";
        temporaryPassword = password != null ? password : "";
    }

    public synchronized String getUsername() {
        return !temporaryUsername.isEmpty()
                ? temporaryUsername
                : prefs.getString(KEY_USERNAME, "");
    }

    public synchronized String getPassword() {
        return !temporaryPassword.isEmpty()
                ? temporaryPassword
                : prefs.getString(KEY_PASSWORD, "");
    }

    public synchronized boolean hasCredentials() {
        return !getUsername().isEmpty() && !getPassword().isEmpty();
    }

    public synchronized boolean hasSavedCredentials() {
        return !prefs.getString(KEY_USERNAME, "").isEmpty()
                && !prefs.getString(KEY_PASSWORD, "").isEmpty();
    }

    public synchronized void clearTemporaryCredentials() {
        temporaryUsername = "";
        temporaryPassword = "";
    }

    public synchronized void clearCredentials() {
        temporaryUsername = "";
        temporaryPassword = "";
        prefs.edit().clear().apply();
    }
}
