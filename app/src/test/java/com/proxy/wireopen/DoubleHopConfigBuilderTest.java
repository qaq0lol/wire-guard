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

/**
 * Regression coverage for the two-hop sing-box route and DNS wiring.
 */
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

    @Test
    public void generatedConfigKeepsSecondHopChainedAndDnsTunneled() throws Exception {
        String privateKey = "aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=";
        String publicKey = "d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=";
        ProxyProfile first = new ProxyProfile(
                "Taiwan", ProtocolType.WIREGUARD,
                wireGuardConfig("first.example.com", privateKey, publicKey));
        ProxyProfile second = new ProxyProfile(
                "San Francisco", ProtocolType.WIREGUARD,
                wireGuardConfig("second.example.com", privateKey, publicKey));

        JsonObject config = JsonParser.parseString(
                DoubleHopConfigBuilder.build(first, second, "", "")
        ).getAsJsonObject();

        JsonArray endpoints = config.getAsJsonArray("endpoints");
        assertEquals(2, endpoints.size());
        assertEquals("hop1", endpoints.get(0).getAsJsonObject().get("tag").getAsString());
        assertEquals("hop2", endpoints.get(1).getAsJsonObject().get("tag").getAsString());
        assertEquals("hop1", endpoints.get(1).getAsJsonObject().get("detour").getAsString());

        JsonArray dnsServers = config.getAsJsonObject("dns").getAsJsonArray("servers");
        assertEquals("dns-bootstrap", dnsServers.get(0).getAsJsonObject().get("tag").getAsString());
        assertFalse("Bootstrap DNS must not detour to an empty direct outbound",
                dnsServers.get(0).getAsJsonObject().has("detour"));
        assertEquals("hop2", dnsServers.get(1).getAsJsonObject().get("detour").getAsString());

        JsonObject route = config.getAsJsonObject("route");
        assertEquals("dns-bootstrap", route.get("default_domain_resolver").getAsString());
        assertEquals("hop2", route.get("final").getAsString());
        assertNotNull(config.getAsJsonArray("inbounds"));
    }
}
