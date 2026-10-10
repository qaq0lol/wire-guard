package com.proxy.wireopen.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.proxy.wireopen.model.PresetPortItem;
import com.proxy.wireopen.model.ProtocolType;
import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.parser.OpenVpnConfigParser;
import com.proxy.wireopen.parser.WireGuardConfigParser;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for managing WireGuard and OpenVPN proxy profiles with derived port support.
 */
public class ProfileRepository {
    private static final String PREF_NAME = "wireopen_profiles";
    private static final String KEY_PROFILES = "saved_profiles";
    private static final String KEY_ACTIVE_ID = "active_profile_id";

    private final Context context;
    private final SharedPreferences prefs;
    private final Gson gson;
    private final List<ProxyProfile> profiles = new ArrayList<>();
    private String activeProfileId = null;

    public ProfileRepository(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        if (context != null) {
            this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        } else {
            this.prefs = null;
        }
        this.gson = new Gson();
        load();
    }

    public synchronized List<ProxyProfile> getProfiles() {
        return new ArrayList<>(profiles);
    }

    public synchronized ProxyProfile getActiveProfile() {
        if (activeProfileId != null) {
            // Check direct profile match
            for (ProxyProfile p : profiles) {
                if (p.getId().equals(activeProfileId)) {
                    return p;
                }
            }

            // Check if activeProfileId is a derived port identifier
            if (activeProfileId.startsWith("port_") || activeProfileId.startsWith("preset_")) {
                PresetPortItem preset = PresetRegionManager.getInstance().findPortByUniqueId(activeProfileId);
                if (preset != null) {
                    if (preset.getProfileId() != null) {
                        for (ProxyProfile p : profiles) {
                            if (p.getId().equals(preset.getProfileId())) {
                                return p;
                            }
                        }
                    }
                    return preset.toProxyProfile();
                }
            }
        }

        if (!profiles.isEmpty()) {
            ProxyProfile fallback = profiles.get(0);
            if (fallback != null && fallback.getId() != null
                    && !fallback.getId().equals(activeProfileId)) {
                activeProfileId = fallback.getId();
                save();
            }
            return fallback;
        }
        return null;
    }

    public synchronized void setActiveProfileId(String id) {
        this.activeProfileId = id;
        save();
    }

    public synchronized String getActiveProfileId() {
        return activeProfileId;
    }

    public synchronized void addProfile(ProxyProfile profile) {
        profiles.add(profile);
        if (activeProfileId == null) {
            activeProfileId = profile.getId();
        }
        saveProfileToDisk(profile);
        PresetRegionManager.getInstance().syncFromProfiles(profiles);
        save();
    }

    public synchronized void updateProfile(ProxyProfile profile) {
        if (profile == null || profile.getId() == null) return;
        for (int i = 0; i < profiles.size(); i++) {
            ProxyProfile oldProfile = profiles.get(i);
            if (oldProfile != null && profile.getId().equals(oldProfile.getId())) {
                deleteStoredProfileFiles(oldProfile);
                profiles.set(i, profile);
                saveProfileToDisk(profile);
                PresetRegionManager.getInstance().syncFromProfiles(profiles);
                save();
                return;
            }
        }
    }

    /**
     * Legacy method retained for API compatibility; multi-port dynamic overwriting is removed
     * to keep config files authentic and pure.
     */
    @Deprecated
    public synchronized ProxyProfile switchProfilePort(String profileId, int newPort, String transport) {
        return getProfileById(profileId);
    }

    private void saveProfileToDisk(ProxyProfile profile) {
        if (profile == null || profile.getRawConfig() == null) return;
        String ext = (profile.getProtocolType() == ProtocolType.WIREGUARD) ? ".conf" : ".ovpn";
        saveConfigFileToDisk(profile.getId() + ext, profile.getRawConfig());
        if (profile.getName() != null && !profile.getName().trim().isEmpty()) {
            String safeName = profile.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!safeName.endsWith(ext)) {
                safeName = safeName + ext;
            }
            saveConfigFileToDisk(safeName, profile.getRawConfig());
        }
    }

    private void deleteStoredProfileFiles(ProxyProfile profile) {
        if (profile == null || profile.getId() == null) return;
        String ext = (profile.getProtocolType() == ProtocolType.WIREGUARD) ? ".conf" : ".ovpn";
        deleteConfigFileFromDisk(profile.getId() + ext);
        if (profile.getName() != null && !profile.getName().trim().isEmpty()) {
            String safeName = profile.getName()
                    .replace((char) 92, '_')
                    .replace('/', '_')
                    .replace(':', '_')
                    .replace('*', '_')
                    .replace('?', '_')
                    .replace('"', '_')
                    .replace('<', '_')
                    .replace('>', '_')
                    .replace('|', '_');
            if (!safeName.endsWith(ext)) {
                safeName = safeName + ext;
            }
            deleteConfigFileFromDisk(safeName);
        }
    }

    private void deleteConfigFileFromDisk(String filename) {
        if (filename == null || filename.isEmpty()) return;
        try {
            java.io.File baseDir;
            if (context != null) {
                baseDir = new java.io.File(context.getFilesDir(), "profiles");
            } else {
                baseDir = new java.io.File(System.getProperty("java.io.tmpdir", "."), "profiles");
            }
            java.io.File targetFile = new java.io.File(baseDir, filename);
            java.io.File canonicalBase = baseDir.getCanonicalFile();
            java.io.File canonicalTarget = targetFile.getCanonicalFile();
            if (canonicalTarget.toPath().startsWith(canonicalBase.toPath())) {
                java.nio.file.Files.deleteIfExists(canonicalTarget.toPath());
            }
        } catch (Exception ignored) {}
    }

    private void saveConfigFileToDisk(String filename, String content) {
        if (content == null) return;
        try {
            java.io.File baseDir;
            if (context != null) {
                baseDir = new java.io.File(context.getFilesDir(), "profiles");
            } else {
                baseDir = new java.io.File(System.getProperty("java.io.tmpdir", "."), "profiles");
            }
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            java.io.File targetFile = new java.io.File(baseDir, filename);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
                fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    public synchronized ProxyProfile getProfileById(String id) {
        if (id == null) return null;
        for (ProxyProfile p : profiles) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        if (id.startsWith("port_") || id.startsWith("preset_")) {
            PresetPortItem preset = PresetRegionManager.getInstance().findPortByUniqueId(id);
            if (preset != null) {
                if (preset.getProfileId() != null) {
                    for (ProxyProfile p : profiles) {
                        if (p.getId().equals(preset.getProfileId())) {
                            return p;
                        }
                    }
                }
                return preset.toProxyProfile();
            }
        }
        return null;
    }

    public synchronized void deleteProfile(String id) {
        if (id == null) return;
        ProxyProfile removed = null;
        for (ProxyProfile profile : profiles) {
            if (profile != null && id.equals(profile.getId())) {
                removed = profile;
                break;
            }
        }
        if (removed != null) {
            deleteStoredProfileFiles(removed);
        }
        profiles.removeIf(p -> p != null && id.equals(p.getId()));
        if (id.equals(activeProfileId)) {
            activeProfileId = profiles.isEmpty() ? null : profiles.get(0).getId();
        }
        PresetRegionManager.getInstance().syncFromProfiles(profiles);
        save();
    }

    /**
     * Imports a WireGuard configuration file content.
     */
    public ProxyProfile importWireGuard(String name, String content) throws Exception {
        ProxyProfile profile = new ProxyProfile(name, ProtocolType.WIREGUARD, content);
        profile.setWireGuardConfig(WireGuardConfigParser.parse(content));
        addProfile(profile);
        return profile;
    }

    /**
     * Imports an OpenVPN configuration file content.
     */
    public ProxyProfile importOpenVpn(String name, String content) throws Exception {
        ProxyProfile profile = new ProxyProfile(name, ProtocolType.OPENVPN, content);
        profile.setOpenVpnConfig(OpenVpnConfigParser.parse(content));
        addProfile(profile);
        return profile;
    }

    /**
     * Automatically detect protocol and import.
     */
    public ProxyProfile autoImport(String name, String content) throws Exception {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("配置内容不能为空");
        }
        String lower = content.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("[interface]") || lower.contains("[peer]")) {
            return importWireGuard(name, content);
        } else if (lower.contains("client") || lower.contains("dev tun") || lower.contains("dev tap") || lower.contains("<ca>") || lower.contains("remote ")) {
            return importOpenVpn(name, content);
        } else {
            try {
                return importWireGuard(name, content);
            } catch (Exception e) {
                return importOpenVpn(name, content);
            }
        }
    }

    /**
     * Automatically loads bundled profiles from assets/bundled_nodes/ if repository is empty.
     */
    public synchronized void loadBundledProfilesIfEmpty(Context ctx) {
        if (!profiles.isEmpty()) {
            return;
        }
        Context appContext = (ctx != null) ? ctx.getApplicationContext() : this.context;
        if (appContext == null) {
            return;
        }
        try {
            android.content.res.AssetManager am = appContext.getAssets();
            String[] list = am.list("bundled_nodes");
            if (list == null || list.length == 0) {
                return;
            }
            java.util.Arrays.sort(list);
            for (String filename : list) {
                if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".conf")) {
                    continue;
                }
                try (java.io.InputStream is = am.open("bundled_nodes/" + filename);
                     java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = is.read(buf)) != -1) {
                        baos.write(buf, 0, len);
                    }
                    String content = baos.toString("UTF-8");
                    String profileName = filename.replaceAll("(?i)\\.conf$", "");
                    ProxyProfile profile = new ProxyProfile(profileName, ProtocolType.WIREGUARD, content);
                    profile.setWireGuardConfig(WireGuardConfigParser.parse(content));
                    profiles.add(profile);
                    saveProfileToDisk(profile);
                } catch (Exception e) {
                    android.util.Log.w("ProfileRepository", "Failed to load bundled profile: " + filename, e);
                }
            }
            if (!profiles.isEmpty()) {
                if (activeProfileId == null) {
                    activeProfileId = profiles.get(0).getId();
                }
                PresetRegionManager.getInstance().syncFromProfiles(profiles);
                save();
            }
        } catch (Exception e) {
            android.util.Log.e("ProfileRepository", "Failed to load bundled profiles from assets", e);
        }
    }

    private void load() {
        profiles.clear();
        if (prefs != null) {
            String json = prefs.getString(KEY_PROFILES, null);
            if (json != null) {
                try {
                    Type type = new TypeToken<List<ProxyProfile>>() {}.getType();
                    List<ProxyProfile> loaded = gson.fromJson(json, type);
                    if (loaded != null) {
                        profiles.addAll(loaded);
                    }
                    activeProfileId = prefs.getString(KEY_ACTIVE_ID, null);
                } catch (Exception ignored) {}
            }
        }

        if (activeProfileId == null && !profiles.isEmpty()) {
            activeProfileId = profiles.get(0).getId();
        }

        PresetRegionManager.getInstance().syncFromProfiles(profiles);
    }

    private void save() {
        if (prefs != null) {
            String json = gson.toJson(profiles);
            prefs.edit()
                    .putString(KEY_PROFILES, json)
                    .putString(KEY_ACTIVE_ID, activeProfileId)
                    .apply();
        }
    }
}
