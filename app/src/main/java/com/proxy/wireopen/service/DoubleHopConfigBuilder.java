package com.proxy.wireopen.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.proxy.wireopen.model.ProtocolType;
import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.model.WireGuardConfig;
import com.proxy.wireopen.parser.WireGuardConfigParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts two imported profiles into one sing-box instance for two-hop chaining.
 * The second endpoint's dialer is detoured through the first endpoint, while the
 * single Android TUN routes user traffic through the second endpoint.
 */
public final class DoubleHopConfigBuilder {
    private DoubleHopConfigBuilder() {}

    public static boolean requiresCredentials(ProxyProfile profile) {
        return false;
    }

    public static String build(ProxyProfile first, ProxyProfile second, String username, String password)
            throws Exception {
        if (first == null || second == null) {
            throw new IllegalArgumentException("请选择第一跳和第二跳节点");
        }
        if (first.getId() != null && first.getId().equals(second.getId())) {
            throw new IllegalArgumentException("第一跳和第二跳不能选择同一个节点");
        }
        if (first.getProtocolType() != ProtocolType.WIREGUARD || second.getProtocolType() != ProtocolType.WIREGUARD) {
            throw new IllegalArgumentException("双跳功能目前仅支持 WireGuard 协议节点");
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
        endpoints.add(buildWireGuardEndpoint(first, "hop1", null));
        endpoints.add(buildWireGuardEndpoint(second, "hop2", "hop1"));
        root.add("endpoints", endpoints);

        JsonObject dns = new JsonObject();
        JsonArray servers = new JsonArray();

        JsonObject bootstrapDns = new JsonObject();
        bootstrapDns.addProperty("tag", "dns-bootstrap");
        bootstrapDns.addProperty("address", "223.5.5.5");
        bootstrapDns.addProperty("strategy", "ipv4_only");

        JsonObject tunnelDns = new JsonObject();
        tunnelDns.addProperty("tag", "dns-tunnel");
        tunnelDns.addProperty("address", "1.1.1.1");
        tunnelDns.addProperty("detour", "hop2");

        servers.add(bootstrapDns);
        servers.add(tunnelDns);
        dns.add("servers", servers);

        JsonArray dnsRules = new JsonArray();
        List<String> rawHosts = extractServerHosts(first, second);
        JsonArray domains = new JsonArray();
        for (String rawHost : rawHosts) {
            if (rawHost != null && !rawHost.trim().isEmpty() && !isIpAddress(rawHost.trim())) {
                domains.add(rawHost.trim());
            }
        }
        if (domains.size() > 0) {
            JsonObject directDnsRule = new JsonObject();
            directDnsRule.add("domain", domains);
            directDnsRule.addProperty("server", "dns-bootstrap");
            dnsRules.add(directDnsRule);
        }

        dns.add("rules", dnsRules);
        dns.addProperty("final", "dns-tunnel");
        dns.addProperty("independent_cache", true);
        root.add("dns", dns);

        JsonObject route = new JsonObject();
        JsonArray rules = new JsonArray();

        JsonObject dnsRule = new JsonObject();
        dnsRule.addProperty("protocol", "dns");
        dnsRule.addProperty("action", "hijack-dns");
        rules.add(dnsRule);

        route.add("rules", rules);
        route.addProperty("default_domain_resolver", "dns-bootstrap");
        route.addProperty("final", "hop2");
        root.add("route", route);

        return root.toString();
    }

    private static List<String> extractServerHosts(ProxyProfile first, ProxyProfile second) {
        List<String> hosts = new ArrayList<>();
        try {
            WireGuardConfig c1 = WireGuardConfigParser.parse(first.getRawConfig());
            if (c1.isValid() && c1.getEndpointHost() != null) hosts.add(c1.getEndpointHost());
        } catch (Exception ignored) {}
        try {
            WireGuardConfig c2 = WireGuardConfigParser.parse(second.getRawConfig());
            if (c2.isValid() && c2.getEndpointHost() != null) hosts.add(c2.getEndpointHost());
        } catch (Exception ignored) {}
        return hosts;
    }

    private static boolean isIpAddress(String text) {
        return text.matches("^\\d{1,3}(\\.\\d{1,3}){3}$") || text.contains(":");
    }

    private static JsonObject buildWireGuardEndpoint(ProxyProfile profile, String tag, String detour) throws Exception {
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
}
