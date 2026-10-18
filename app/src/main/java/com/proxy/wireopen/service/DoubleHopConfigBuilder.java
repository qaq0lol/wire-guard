package com.proxy.wireopen.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.proxy.wireopen.model.OpenVpnConfig;
import com.proxy.wireopen.model.ProtocolType;
import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.model.WireGuardConfig;
import com.proxy.wireopen.parser.OpenVpnConfigParser;
import com.proxy.wireopen.parser.WireGuardConfigParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converts two imported profiles into one sing-box instance.
 * The second endpoint's dialer is detoured through the first endpoint, while the
 * single Android TUN routes user traffic through the second endpoint.
 */
public final class DoubleHopConfigBuilder {
    private DoubleHopConfigBuilder() {}

    public static boolean requiresCredentials(ProxyProfile profile) {
        return profile != null
                && profile.getProtocolType() == ProtocolType.OPENVPN
                && OpenVpnConfigParserNeedsCredentials(profile.getRawConfig());
    }

    private static boolean OpenVpnConfigParserNeedsCredentials(String raw) {
        return raw != null && com.proxy.wireopen.engine.OpenVpnEngine.requiresUserPass(raw);
    }

    public static String build(ProxyProfile first, ProxyProfile second, String username, String password)
            throws Exception {
        if (first == null || second == null) {
            throw new IllegalArgumentException("请选择第一跳和第二跳节点");
        }
        if (first.getId() != null && first.getId().equals(second.getId())) {
            throw new IllegalArgumentException("第一跳和第二跳不能选择同一个节点");
        }
        if (first.getProtocolType() == null || second.getProtocolType() == null) {
            throw new IllegalArgumentException("节点协议类型无效");
        }

        JsonObject root = new JsonObject();
        JsonObject log = new JsonObject();
        log.addProperty("level", "info");
        log.addProperty("timestamp", true);
        root.add("log", log);

        JsonObject inbound = new JsonObject();
        inbound.addProperty("type", "tun");
        inbound.addProperty("tag", "tun-in");
        JsonArray tunAddresses = new JsonArray();
        tunAddresses.add("172.19.0.1/30");
        tunAddresses.add("fdfe:dcba:9876::1/126");
        inbound.add("address", tunAddresses);
        inbound.addProperty("auto_route", true);
        inbound.addProperty("strict_route", true);
        inbound.addProperty("stack", "system");
        inbound.addProperty("mtu", 1400);
        JsonArray inbounds = new JsonArray();
        inbounds.add(inbound);
        root.add("inbounds", inbounds);

        JsonArray endpoints = new JsonArray();
        endpoints.add(buildEndpoint(first, "hop1", null, username, password));
        endpoints.add(buildEndpoint(second, "hop2", "hop1", username, password));
        root.add("endpoints", endpoints);

        JsonArray outbounds = new JsonArray();
        JsonObject direct = new JsonObject();
        direct.addProperty("type", "direct");
        direct.addProperty("tag", "direct");
        outbounds.add(direct);
        root.add("outbounds", outbounds);

        JsonArray dnsServers = new JsonArray();
        JsonObject bootstrapDns = new JsonObject();
        bootstrapDns.addProperty("type", "udp");
        bootstrapDns.addProperty("tag", "dns-bootstrap");
        bootstrapDns.addProperty("server", "1.1.1.1");
        bootstrapDns.addProperty("server_port", 53);
        bootstrapDns.addProperty("detour", "direct");
        dnsServers.add(bootstrapDns);

        JsonObject tunneledDns = new JsonObject();
        tunneledDns.addProperty("type", "udp");
        tunneledDns.addProperty("tag", "dns-hop2");
        tunneledDns.addProperty("server", "1.1.1.1");
        tunneledDns.addProperty("server_port", 53);
        tunneledDns.addProperty("detour", "hop2");
        dnsServers.add(tunneledDns);

        JsonObject dns = new JsonObject();
        dns.add("servers", dnsServers);
        dns.addProperty("final", "dns-hop2");
        dns.addProperty("strategy", "prefer_ipv4");
        root.add("dns", dns);

        JsonObject route = new JsonObject();
        route.addProperty("default_domain_resolver", "dns-bootstrap");
        route.addProperty("auto_detect_interface", true);
        JsonArray routeRules = new JsonArray();
        JsonObject dnsRule = new JsonObject();
        dnsRule.addProperty("protocol", "dns");
        dnsRule.addProperty("action", "hijack-dns");
        routeRules.add(dnsRule);
        route.add("rules", routeRules);
        route.addProperty("final", "hop2");
        root.add("route", route);

        return root.toString();
    }

    private static JsonObject buildEndpoint(ProxyProfile profile, String tag, String detour,
                                            String username, String password) throws Exception {
        if (profile.getRawConfig() == null || profile.getRawConfig().trim().isEmpty()) {
            throw new IllegalArgumentException("节点配置为空：" + profile.getName());
        }
        if (profile.getProtocolType() == ProtocolType.WIREGUARD) {
            return buildWireGuard(profile, tag, detour);
        }
        if (profile.getProtocolType() == ProtocolType.OPENVPN) {
            return buildOpenVpn(profile, tag, detour, username, password);
        }
        throw new IllegalArgumentException("不支持的节点协议：" + profile.getProtocolType());
    }

    private static JsonObject buildWireGuard(ProxyProfile profile, String tag, String detour) throws Exception {
        WireGuardConfig cfg = WireGuardConfigParser.parse(profile.getRawConfig());
        if (!cfg.isValid()) {
            throw new IllegalArgumentException("WireGuard 配置缺少私钥、地址、公钥或服务器地址：" + profile.getName());
        }

        JsonObject endpoint = new JsonObject();
        endpoint.addProperty("type", "wireguard");
        endpoint.addProperty("tag", tag);
        endpoint.addProperty("system", false);
        endpoint.addProperty("name", profile.getName());
        endpoint.addProperty("mtu", cfg.getMtu() > 0 ? cfg.getMtu() : 1400);
        endpoint.addProperty("private_key", cfg.getPrivateKey());
        JsonArray addresses = new JsonArray();
        for (String address : cfg.getAddresses()) {
            if (address != null && !address.trim().isEmpty()) addresses.add(address.trim());
        }
        endpoint.add("address", addresses);

        JsonArray peers = new JsonArray();
        JsonObject peer = new JsonObject();
        peer.addProperty("address", cfg.getEndpointHost());
        peer.addProperty("port", cfg.getEndpointPort());
        peer.addProperty("public_key", cfg.getPublicKey());
        if (cfg.getPresharedKey() != null && !cfg.getPresharedKey().trim().isEmpty()) {
            peer.addProperty("pre_shared_key", cfg.getPresharedKey().trim());
        }
        JsonArray allowedIps = new JsonArray();
        allowedIps.add("0.0.0.0/0");
        allowedIps.add("::/0");
        peer.add("allowed_ips", allowedIps);
        if (cfg.getPersistentKeepalive() > 0) {
            peer.addProperty("persistent_keepalive_interval", cfg.getPersistentKeepalive());
        }
        peers.add(peer);
        endpoint.add("peers", peers);
        if (detour != null) endpoint.addProperty("detour", detour);
        return endpoint;
    }

    private static JsonObject buildOpenVpn(ProxyProfile profile, String tag, String detour,
                                           String username, String password) throws Exception {
        String raw = profile.getRawConfig();
        OpenVpnConfig cfg = OpenVpnConfigParser.parse(raw);
        if (!cfg.isValid()) {
            throw new IllegalArgumentException("OpenVPN 配置缺少服务器地址或端口：" + profile.getName());
        }
        if ("secret".equalsIgnoreCase(cfg.getTlsAuthTag())) {
            throw new IllegalArgumentException("双跳暂不支持静态密钥 OpenVPN（secret）配置，请使用 TLS 模式配置");
        }
        if (cfg.isAuthUserPass() && ((username == null || username.isEmpty())
                || (password == null || password.isEmpty()))) {
            throw new IllegalArgumentException("OpenVPN 节点需要账号密码，请先填写 OpenVPN 凭据");
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (hasExternalCredentialFile(raw)) {
            throw new IllegalArgumentException("双跳模式暂不支持依赖外部文件的 OpenVPN 证书/密钥配置，请改用内嵌证书配置");
        }

        JsonObject endpoint = new JsonObject();
        endpoint.addProperty("type", "openvpn-client");
        endpoint.addProperty("tag", tag);
        boolean staticKeyMode = cfg.getTlsAuthTag() != null && "secret".equals(cfg.getTlsAuthTag());
        endpoint.addProperty("mode", staticKeyMode ? "static_key" : "tls");
        endpoint.addProperty("server", cfg.getRemoteHost());
        endpoint.addProperty("server_port", cfg.getRemotePort());
        endpoint.addProperty("network", "tcp".equalsIgnoreCase(cfg.getProtocol()) ? "tcp" : "udp");
        endpoint.addProperty("system", false);
        endpoint.addProperty("name", profile.getName());
        endpoint.addProperty("mtu", cfg.getMtu() > 0 ? cfg.getMtu() : 1400);
        endpoint.addProperty("redirect_gateway", true);
        endpoint.addProperty("username", username == null ? "" : username);
        endpoint.addProperty("password", password == null ? "" : password);
        endpoint.addProperty("auth_retry", "none");
        endpoint.addProperty("auth", cfg.getAuth() == null ? "" : cfg.getAuth());

        JsonArray addresses = new JsonArray();
        endpoint.add("address", addresses);

        JsonObject tls = new JsonObject();
        tls.addProperty("server_name", cfg.getRemoteHost());
        tls.addProperty("version_min", "1.2");
        if (cfg.getCaCert() != null && !cfg.getCaCert().trim().isEmpty()) {
            JsonArray ca = new JsonArray();
            ca.add(cfg.getCaCert());
            tls.add("certificate", ca);
        }
        if (cfg.getClientCert() != null && !cfg.getClientCert().trim().isEmpty()) {
            JsonArray cert = new JsonArray();
            cert.add(cfg.getClientCert());
            tls.add("client_certificate", cert);
        }
        if (cfg.getClientKey() != null && !cfg.getClientKey().trim().isEmpty()) {
            JsonArray key = new JsonArray();
            key.add(cfg.getClientKey());
            tls.add("client_key", key);
        }
        if (cfg.getTlsAuthKey() != null && !cfg.getTlsAuthKey().trim().isEmpty()
                && cfg.getTlsAuthTag() != null && !"secret".equals(cfg.getTlsAuthTag())) {
            JsonObject wrap = new JsonObject();
            String wrapType;
            switch (cfg.getTlsAuthTag().toLowerCase(Locale.ROOT)) {
                case "tls-crypt": wrapType = "tls_crypt"; break;
                case "tls-crypt-v2": wrapType = "tls_crypt_v2"; break;
                default: wrapType = "tls_auth"; break;
            }
            wrap.addProperty("type", wrapType);
            JsonArray key = new JsonArray();
            key.add(cfg.getTlsAuthKey());
            wrap.add("key", key);
            if ("tls_auth".equals(wrapType) && cfg.getTlsAuthDirection() != -1) {
                wrap.addProperty("direction", cfg.getTlsAuthDirection() == 1 ? "client" : "server");
            }
            tls.add("control_wrap", wrap);
        }
        String tlsMin = findDirective(raw, "tls-version-min");
        if (tlsMin != null) {
            String value = tlsMin.split("\\s+")[0];
            if (value.matches("1\\.[0-3]")) tls.addProperty("version_min", value);
        }
        String tlsServerName = findDirective(raw, "tls-server-name");
        if (tlsServerName != null && !tlsServerName.isEmpty()) tls.addProperty("server_name", tlsServerName);
        String remoteCert = findDirective(raw, "remote-cert-tls");
        if (remoteCert != null && !remoteCert.isEmpty()) tls.addProperty("remote_certificate_tls", remoteCert);
        if (!staticKeyMode) endpoint.add("tls", tls);

        if (staticKeyMode) {
            if (cfg.getTlsAuthKey() == null || cfg.getTlsAuthKey().trim().isEmpty()) {
                throw new IllegalArgumentException("静态密钥 OpenVPN 配置缺少内嵌 <secret> 密钥");
            }
            JsonArray staticKey = new JsonArray();
            staticKey.add(cfg.getTlsAuthKey());
            endpoint.add("static_key", staticKey);
            if (cfg.getTlsAuthDirection() != -1) {
                endpoint.addProperty("key_direction", cfg.getTlsAuthDirection() == 1 ? "client" : "server");
            }
            endpoint.addProperty("cipher", cfg.getCipher() == null ? "" : cfg.getCipher());
        } else {
            String dataCiphers = findDirective(raw, "data-ciphers");
            if (dataCiphers != null) {
                JsonArray ciphers = new JsonArray();
                for (String item : dataCiphers.split("[:,]")) {
                    if (!item.trim().isEmpty()) ciphers.add(item.trim());
                }
                if (ciphers.size() > 0) endpoint.add("data_ciphers", ciphers);
            }
            String fallback = findDirective(raw, "data-ciphers-fallback");
            if (fallback != null && !fallback.isEmpty()) endpoint.addProperty("data_ciphers_fallback", fallback);
            String compress = findDirective(raw, "compress");
            String compLzo = findDirective(raw, "comp-lzo");
            if (compress != null) {
                endpoint.addProperty("compression", compress);
                endpoint.addProperty("allow_compression", "yes");
            } else if (compLzo != null) {
                endpoint.addProperty("compression_lzo", compLzo.isEmpty() ? "yes" : compLzo);
                endpoint.addProperty("allow_compression", "yes");
            }
            if (cfg.getCipher() != null && !cfg.getCipher().isEmpty() && !cfg.getCipher().equalsIgnoreCase("AES-256-GCM")) {
                endpoint.addProperty("data_ciphers_fallback", cfg.getCipher());
            }
        }
        if (detour != null) endpoint.addProperty("detour", detour);
        return endpoint;
    }

    private static boolean hasExternalCredentialFile(String raw) {
        String[] directives = {"ca", "cert", "key", "tls-auth", "tls-crypt", "pkcs12", "secret"};
        for (String rawLine : raw.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";") || line.startsWith("<")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                for (String directive : directives) {
                    if (parts[0].equalsIgnoreCase(directive)
                            && !parts[1].startsWith("<")
                            && !(parts.length == 2 && parts[1].equalsIgnoreCase("inline"))) {
                        // If there is an inline block, the opening block is handled above.
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String findDirective(String raw, String name) {
        for (String rawLine : raw.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";") || line.startsWith("<")) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts[0].equalsIgnoreCase(name)) {
                return parts.length > 1 ? parts[1].trim() : "";
            }
        }
        return null;
    }
}
