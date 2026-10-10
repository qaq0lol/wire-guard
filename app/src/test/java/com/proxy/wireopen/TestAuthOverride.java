package com.proxy.wireopen;

import com.proxy.wireopen.engine.OpenVpnEngine;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestAuthOverride {

    @Test
    public void testAuthType() {
        assertFalse(OpenVpnEngine.requiresUserPass(
                "client\ndev tun\nremote 1.1.1.1 1194\n"
        ));

        assertFalse(OpenVpnEngine.requiresUserPass(
                "client\nauth-user-passive foo\n"
        ));

        assertTrue(OpenVpnEngine.requiresUserPass(
                "client\nauth-user-pass\n"
        ));

        assertTrue(OpenVpnEngine.requiresUserPass(
                "client\nauth-user-pass credentials.txt\n"
        ));

        assertTrue(OpenVpnEngine.requiresUserPass(
                "client\nauth-user-pass # username/password required\n"
        ));

        assertFalse(OpenVpnEngine.requiresUserPass(
                "client\n# auth-user-pass\n; auth-user-pass\n"
        ));

        assertTrue(OpenVpnEngine.isTcpConfig(
                "client\nproto tcp-client\nremote vpn.example.com 443\n"
        ));
        assertFalse(OpenVpnEngine.isTcpConfig(
                "client\nproto udp\nremote vpn.example.com 1194\n"
        ));
        assertEquals(30_000L, OpenVpnEngine.getConnectAttemptTimeoutMillis(
                "client\nproto tcp\nremote vpn.example.com 443\n"
        ));
        assertEquals(45_000L, OpenVpnEngine.getConnectAttemptTimeoutMillis(
                "client\nproto udp\nremote vpn.example.com 1194\n"
        ));
    }
}
