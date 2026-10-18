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

        JsonArray outbounds = config.getAsJsonArray("outbounds");
        assertEquals(3, outbounds.size());
        assertEquals("hop1", outbounds.get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals("hop2", outbounds.get(1).getAsJsonObject().get("tag").getAsString());
        assertEquals("hop1", outbounds.get(1).getAsJsonObject().get("detour").getAsString());
        assertEquals("direct", outbounds.get(2).getAsJsonObject().get("tag").getAsString());

        JsonObject hop1Peer = outbounds.get(0).getAsJsonObject().getAsJsonArray("peers").get(0).getAsJsonObject();
        assertEquals("first.example.com", hop1Peer.get("server").getAsString());
        assertEquals(51820, hop1Peer.get("server_port").getAsInt());

        JsonArray dnsServers = config.getAsJsonObject("dns").getAsJsonArray("servers");
        assertEquals("dns-bootstrap", dnsServers.get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals("udp", dnsServers.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("223.5.5.5", dnsServers.get(0).getAsJsonObject().get("server").getAsString());
        assertEquals("direct", dnsServers.get(0).getAsJsonObject().get("detour").getAsString());
        assertEquals("hop2", dnsServers.get(1).getAsJsonObject().get("detour").getAsString());
        assertEquals("udp", dnsServers.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("1.1.1.1", dnsServers.get(1).getAsJsonObject().get("server").getAsString());

        JsonObject route = config.getAsJsonObject("route");
        assertEquals("dns-bootstrap", route.get("default_domain_resolver").getAsString());
        assertEquals("hop2", route.get("final").getAsString());
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
