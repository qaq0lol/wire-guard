package com.proxy.wireopen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.proxy.wireopen.model.ProtocolType;
import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.service.DoubleHopConfigBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class DoubleHopConfigBuilderTest {

    private static String wireGuardConfig(String host, String privateKey, String publicKey) {
        return "[Interface]\n"
                + "PrivateKey = " + privateKey + "\n"
                + "Address = 10.0.0.2/32\n\n"
                + "[Peer]\n"
                + "PublicKey = " + publicKey + "\n"
                + "Endpoint = " + host + ":51820\n"
                + "AllowedIPs = 0.0.0.0/0, ::/0\n";
    }

    private static String openVpnConfig(String host) {
        return "client\n"
                + "dev tun\n"
                + "proto udp\n"
                + "remote " + host + " 1194\n";
    }

    @Test
    public void generatedConfigKeepsSecondHopChainedAndDnsTunneled() throws Exception {
        String privateKey = "aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=";
        String publicKey = "d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=";
        ProxyProfile first = new ProxyProfile(
                "Taiwan", ProtocolType.WIREGUARD,
                wireGuardConfig("first.example.com", privateKey, publicKey));
        first.setId("id-taiwan");

        ProxyProfile second = new ProxyProfile(
                "San Francisco", ProtocolType.WIREGUARD,
                wireGuardConfig("second.example.com", privateKey, publicKey));
        second.setId("id-usa");

        JsonObject config = JsonParser.parseString(
                DoubleHopConfigBuilder.build(first, second, "", "")
        ).getAsJsonObject();

        JsonArray endpoints = config.getAsJsonArray("endpoints");
        assertEquals(2, endpoints.size());
        assertEquals("hop1", endpoints.get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals("hop2", endpoints.get(1).getAsJsonObject().get("tag").getAsString());
        assertEquals("hop1", endpoints.get(1).getAsJsonObject().get("detour").getAsString());

        JsonObject hop1Peer = endpoints.get(0).getAsJsonObject().getAsJsonArray("peers").get(0).getAsJsonObject();
        assertEquals("first.example.com", hop1Peer.get("address").getAsString());
        assertEquals(51820, hop1Peer.get("port").getAsInt());

        JsonArray dnsServers = config.getAsJsonObject("dns").getAsJsonArray("servers");
        assertEquals("dns-bootstrap", dnsServers.get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals("udp", dnsServers.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("223.5.5.5", dnsServers.get(0).getAsJsonObject().get("server").getAsString());
        assertFalse("Bootstrap DNS must not detour to an empty direct outbound",
                dnsServers.get(0).getAsJsonObject().has("detour"));
        assertEquals("hop2", dnsServers.get(1).getAsJsonObject().get("detour").getAsString());
        assertEquals("udp", dnsServers.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("1.1.1.1", dnsServers.get(1).getAsJsonObject().get("server").getAsString());

        JsonArray outbounds = config.getAsJsonArray("outbounds");
        assertEquals(1, outbounds.size());
        assertEquals("direct", outbounds.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("direct", outbounds.get(0).getAsJsonObject().get("tag").getAsString());

        JsonObject route = config.getAsJsonObject("route");
        assertEquals("dns-bootstrap", route.get("default_domain_resolver").getAsString());
        assertEquals("direct", route.get("final").getAsString());
        JsonArray routeRules = route.getAsJsonArray("rules");
        assertEquals("hijack-dns", routeRules.get(0).getAsJsonObject().get("action").getAsString());
        JsonObject tunnelRoute = routeRules.get(1).getAsJsonObject();
        assertEquals("route", tunnelRoute.get("action").getAsString());
        assertEquals("hop2", tunnelRoute.get("outbound").getAsString());
        assertEquals("tun-in", tunnelRoute.getAsJsonArray("inbound").get(0).getAsString());
        assertNotNull(config.getAsJsonArray("inbounds"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonWireGuardProfile() throws Exception {
        String privateKey = "aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=";
        String publicKey = "d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=";
        ProxyProfile first = new ProxyProfile(
                "Taiwan", ProtocolType.WIREGUARD,
                wireGuardConfig("first.example.com", privateKey, publicKey));
        first.setId("id-taiwan");

        ProxyProfile second = new ProxyProfile(
                "OpenVPN Node", ProtocolType.OPENVPN,
                openVpnConfig("ovpn.example.com"));
        second.setId("id-ovpn");

        DoubleHopConfigBuilder.build(first, second, "", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSameProfile() throws Exception {
        String privateKey = "aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=";
        String publicKey = "d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=";
        ProxyProfile profile = new ProxyProfile(
                "Taiwan", ProtocolType.WIREGUARD,
                wireGuardConfig("first.example.com", privateKey, publicKey));
        profile.setId("id-taiwan");

        DoubleHopConfigBuilder.build(profile, profile, "", "");
    }
}
