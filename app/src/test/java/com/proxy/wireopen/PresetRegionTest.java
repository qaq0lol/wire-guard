package com.proxy.wireopen;

import com.proxy.wireopen.model.PresetPortItem;
import com.proxy.wireopen.model.PresetRegion;
import com.proxy.wireopen.model.ProtocolType;
import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.repository.PresetRegionManager;
import com.proxy.wireopen.repository.ProfileRepository;
import com.proxy.wireopen.util.PortSpeedTester;
import com.proxy.wireopen.util.RegionDetector;
import com.proxy.wireopen.util.TrafficStatsManager;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PresetRegionTest {

    @Test
    public void testPresetRegionManagerInitialState() {
        PresetRegionManager manager = PresetRegionManager.getInstance();
        manager.clear();
        List<PresetRegion> regions = manager.getRegions();
        Assert.assertNotNull(regions);
        Assert.assertEquals("PresetRegionManager must have NO hardcoded fake regions on startup", 0, regions.size());
    }

    @Test
    public void testRegionDetector() {
        // Australia
        Assert.assertEquals("🇦🇺", RegionDetector.detectFlag("Australia Melbourne Node"));
        Assert.assertTrue(RegionDetector.detectRegionName("Australia Melbourne").contains("澳大利亚"));
        Assert.assertEquals("🇦🇺", RegionDetector.detectFlag("sydney-server-01"));
        Assert.assertTrue(RegionDetector.detectRegionName("sydney-server-01").contains("澳大利亚"));

        // Japan
        Assert.assertEquals("🇯🇵", RegionDetector.detectFlag("Tokyo-JP-HighSpeed"));
        Assert.assertTrue(RegionDetector.detectRegionName("Tokyo-JP-HighSpeed").contains("日本"));
        Assert.assertEquals("🇯🇵", RegionDetector.detectFlag("大阪专线"));
        Assert.assertTrue(RegionDetector.detectRegionName("大阪专线").contains("日本"));

        // US
        Assert.assertEquals("🇺🇸", RegionDetector.detectFlag("US-Los Angeles-Cera"));
        Assert.assertTrue(RegionDetector.detectRegionName("US-Los Angeles-Cera").contains("美国"));
        Assert.assertEquals("🇺🇸", RegionDetector.detectFlag("Silicon Valley BGP"));
        Assert.assertTrue(RegionDetector.detectRegionName("Silicon Valley BGP").contains("美国"));
        Assert.assertEquals("美国 · 芝加哥", RegionDetector.detectRegionName("Chicago-WG-1194"));

        // Singapore
        Assert.assertEquals("🇸🇬", RegionDetector.detectFlag("Singapore SG Premium"));
        Assert.assertTrue(RegionDetector.detectRegionName("Singapore SG Premium").contains("新加坡"));

        // Hong Kong
        Assert.assertEquals("🇭🇰", RegionDetector.detectFlag("HK BGP Direct"));
        Assert.assertTrue(RegionDetector.detectRegionName("HK BGP Direct").contains("香港"));

        // Germany & UK & Korea & Taiwan
        Assert.assertEquals("🇩🇪", RegionDetector.detectFlag("Frankfurt Germany"));
        Assert.assertEquals("🇬🇧", RegionDetector.detectFlag("London UK"));
        Assert.assertEquals("🇰🇷", RegionDetector.detectFlag("Seoul Korea"));
        Assert.assertEquals("🇹🇼", RegionDetector.detectFlag("Taipei Taiwan"));

        // Specific required patterns from task description
        Assert.assertEquals("🇦🇺", RegionDetector.detectFlag("WG1194-Melbourne-Yarra"));
        Assert.assertEquals("🇦🇺", RegionDetector.detectFlag("au.wireopen.com:443"));
        Assert.assertEquals("🇺🇸", RegionDetector.detectFlag("us.wireopen.com:443"));

        // Global fallback and boundary anti-false-positive tests
        Assert.assertEquals("🌐", RegionDetector.detectFlag("unknown-custom-node"));
        Assert.assertEquals("全球代理节点", RegionDetector.detectRegionName("unknown-custom-node"));
        Assert.assertEquals("🌐", RegionDetector.detectFlag("nexus-vpn.conf"));
        Assert.assertEquals("🌐", RegionDetector.detectFlag("status-node.conf"));
        Assert.assertEquals("🌐", RegionDetector.detectFlag("campus-vpn.conf"));
        Assert.assertEquals("🌐", RegionDetector.detectFlag("msg-queue.conf"));
    }

    @Test
    public void testAll72FlagDrawables() {
        String[] countryCodes = new String[] {
            "ae", "al", "aq", "ar", "at", "au", "ba", "be", "bg", "br",
            "ca", "ch", "cl", "co", "cy", "cz", "de", "dk", "ec", "ee",
            "es", "fi", "fr", "gb", "ge", "gr", "gt", "hk", "hr", "hu",
            "id", "ie", "il", "in", "is", "it", "jp", "ke", "kr", "lt",
            "lu", "lv", "md", "mk", "mx", "my", "ng", "nl", "no", "nz",
            "pa", "pe", "ph", "pl", "pt", "py", "ro", "rs", "ru", "se",
            "sg", "si", "sk", "sv", "th", "tr", "tw", "ua", "us", "uy",
            "vn", "za"
        };
        Assert.assertEquals(72, countryCodes.length);
        int fallback = RegionDetector.getFlagDrawableRes("unknown_xyz");
        Assert.assertNotEquals(0, fallback);

        for (String code : countryCodes) {
            int resId = RegionDetector.getFlagDrawableRes(code);
            Assert.assertNotEquals("Country " + code + " must have a non-zero drawable resource", 0, resId);
            Assert.assertNotEquals("Country " + code + " must have its dedicated flag, not fallback flag_global", fallback, resId);
        }

        // Test UK alias
        Assert.assertEquals(RegionDetector.getFlagDrawableRes("gb"), RegionDetector.getFlagDrawableRes("uk"));
        // Test null fallback
        Assert.assertEquals(fallback, RegionDetector.getFlagDrawableRes(null));
    }

    @Test
    public void testWireGuardNativePort() {
        String wgConfig =
                "[Interface]\n" +
                "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                "Address = 10.0.0.2/32\n" +
                "[Peer]\n" +
                "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                "Endpoint = melbourne.example.test:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n";

        ProxyProfile profile = new ProxyProfile("Melbourne WG", ProtocolType.WIREGUARD, wgConfig);
        profile.setId("wg-1");
        PresetRegion region = PresetRegion.fromProfile(profile);

        Assert.assertNotNull(region);
        Assert.assertEquals("🇦🇺", region.getFlag());
        Assert.assertEquals("melbourne.example.test", region.getHost());

        List<PresetPortItem> ports = region.getPorts();
        // WireGuard profile has strictly 1 native port
        Assert.assertEquals("WireGuard profile must have exactly 1 native port", 1, ports.size());

        PresetPortItem p = ports.get(0);
        Assert.assertEquals(ProtocolType.WIREGUARD, p.getProtocolType());
        Assert.assertEquals("UDP", p.getTransport());
        Assert.assertEquals("melbourne.example.test", p.getHost());
        Assert.assertEquals(51820, p.getPort());

        String editedPortConfig = wgConfig.replace(":51820", ":1194");
        ProxyProfile editedProfile = new ProxyProfile("Chicago WG", ProtocolType.WIREGUARD, editedPortConfig);
        editedProfile.setId("wg-custom-port");
        PresetRegion editedRegion = PresetRegion.fromProfile(editedProfile);
        Assert.assertEquals(1194, editedRegion.getPorts().get(0).getPort());
        Assert.assertEquals("WG", editedRegion.getPorts().get(0).getShortProtoTag());

        List<PresetPortItem> wgUdp = region.getPortsByGroup(ProtocolType.WIREGUARD, "UDP");
        Assert.assertEquals(1, wgUdp.size());
        List<PresetPortItem> ovpnUdp = region.getPortsByGroup(ProtocolType.OPENVPN, "UDP");
        Assert.assertEquals(0, ovpnUdp.size());
        List<PresetPortItem> ovpnTcp = region.getPortsByGroup(ProtocolType.OPENVPN, "TCP");
        Assert.assertEquals(0, ovpnTcp.size());
    }

    @Test
    public void testOpenVpnNativePort() {
        String ovpnConfig =
                "client\n" +
                "dev tun\n" +
                "proto udp\n" +
                "remote 203.0.113.5 1194\n" +
                "resolv-retry infinite\n" +
                "<ca>\n---BEGIN CERTIFICATE---\nfake\n---END CERTIFICATE---\n</ca>\n";

        ProxyProfile profile = new ProxyProfile("Tokyo Japan 专线", ProtocolType.OPENVPN, ovpnConfig);
        profile.setId("ovpn-1");
        PresetRegion region = PresetRegion.fromProfile(profile);

        Assert.assertNotNull(region);
        Assert.assertEquals("🇯🇵", region.getFlag());
        Assert.assertEquals("203.0.113.5", region.getHost());

        List<PresetPortItem> ports = region.getPorts();
        // OpenVPN profile has strictly 1 native port
        Assert.assertEquals("OpenVPN profile must have exactly 1 native port", 1, ports.size());

        PresetPortItem p = ports.get(0);
        Assert.assertEquals(ProtocolType.OPENVPN, p.getProtocolType());
        Assert.assertEquals("UDP", p.getTransport());
        Assert.assertEquals("203.0.113.5", p.getHost());
        Assert.assertEquals(1194, p.getPort());

        List<PresetPortItem> ovpnUdp = region.getPortsByGroup(ProtocolType.OPENVPN, "UDP");
        Assert.assertEquals(1, ovpnUdp.size());
        List<PresetPortItem> ovpnTcp = region.getPortsByGroup(ProtocolType.OPENVPN, "TCP");
        Assert.assertEquals(0, ovpnTcp.size());
        List<PresetPortItem> wgUdp = region.getPortsByGroup(ProtocolType.WIREGUARD, "UDP");
        Assert.assertEquals(0, wgUdp.size());
    }

    @Test
    public void testProfilePortPurityPreservedWireGuard() {
        ProfileRepository repo = new ProfileRepository(null);
        String wgConfig =
                "[Interface]\n" +
                "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                "Address = 10.0.0.2/32\n" +
                "[Peer]\n" +
                "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                "Endpoint = 198.51.100.1:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n";

        ProxyProfile profile = new ProxyProfile("US Cera 节点", ProtocolType.WIREGUARD, wgConfig);
        profile.setId("wg-test");
        repo.addProfile(profile);

        // switchProfilePort deprecated: preserves pure native configuration
        ProxyProfile unchanged = repo.switchProfilePort("wg-test", 65142, "UDP");
        Assert.assertNotNull(unchanged);
        Assert.assertEquals(51820, unchanged.getWireGuardConfig().getEndpointPort());
        Assert.assertTrue("Raw config must preserve original port :51820", unchanged.getRawConfig().contains(":51820"));
        Assert.assertFalse("Raw config must NOT contain rewritten port :65142", unchanged.getRawConfig().contains(":65142"));
    }

    @Test
    public void testProfilePortPurityPreservedOpenVpn() {
        ProfileRepository repo = new ProfileRepository(null);
        String ovpnConfig =
                "client\n" +
                "dev tun\n" +
                "proto udp\n" +
                "remote 203.0.113.5 1194\n" +
                "resolv-retry infinite\n" +
                "<ca>\n---BEGIN CERTIFICATE---\nfake\n---END CERTIFICATE---\n</ca>\n";

        ProxyProfile profile = new ProxyProfile("Singapore SG", ProtocolType.OPENVPN, ovpnConfig);
        profile.setId("ovpn-test");
        repo.addProfile(profile);

        // switchProfilePort deprecated: preserves pure native configuration
        ProxyProfile unchanged = repo.switchProfilePort("ovpn-test", 443, "TCP");
        Assert.assertNotNull(unchanged);
        Assert.assertEquals(1194, unchanged.getOpenVpnConfig().getRemotePort());
        Assert.assertEquals("udp", unchanged.getOpenVpnConfig().getProtocol().toLowerCase());
        Assert.assertTrue("Raw config must contain native port 1194", unchanged.getRawConfig().contains("1194"));
        Assert.assertFalse("Raw config must NOT contain rewritten proto tcp", unchanged.getRawConfig().toLowerCase().contains("proto tcp"));
    }

    @Test
    public void testSyncFromProfiles() {
        PresetRegionManager manager = PresetRegionManager.getInstance();
        manager.clear();

        List<ProxyProfile> profiles = new ArrayList<>();
        String wgConfig =
                "[Interface]\n" +
                "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                "Address = 10.0.0.2/32\n" +
                "[Peer]\n" +
                "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                "Endpoint = 198.51.100.1:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n";
        ProxyProfile p1 = new ProxyProfile("Hong Kong HK BGP", ProtocolType.WIREGUARD, wgConfig);
        p1.setId("p1");
        profiles.add(p1);

        manager.syncFromProfiles(profiles);
        List<PresetRegion> regions = manager.getRegions();
        Assert.assertEquals(1, regions.size());
        Assert.assertEquals("🇭🇰", regions.get(0).getFlag());
        Assert.assertEquals(1, regions.get(0).getPorts().size());

        PresetPortItem portItem = manager.findPortByUniqueId(regions.get(0).getPorts().get(0).getUniqueId());
        Assert.assertNotNull("Should find port by uid", portItem);
        Assert.assertEquals(51820, portItem.getPort());
    }

    @Test
    public void testPortSpeedTesterAsync() throws InterruptedException {
        String wgConfig =
                "[Interface]\n" +
                "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                "Address = 10.0.0.2/32\n" +
                "[Peer]\n" +
                "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                "Endpoint = 127.0.0.1:51820\n" +
                "AllowedIPs = 0.0.0.0/0\n";
        ProxyProfile profile = new ProxyProfile("Localhost Test", ProtocolType.WIREGUARD, wgConfig);
        profile.setId("test-p");
        PresetRegion region = PresetRegion.fromProfile(profile);
        PresetPortItem testItem = region.getPorts().get(0);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean callbackTriggered = new AtomicBoolean(false);

        PortSpeedTester.testSinglePort(testItem, new PortSpeedTester.SpeedTestListener() {
            @Override
            public void onPortTested(PresetPortItem item, long latencyMs) {
                callbackTriggered.set(true);
                latch.countDown();
            }

            @Override
            public void onAllCompleted(int successCount, int totalCount) {}
        });

        boolean finished = latch.await(4, TimeUnit.SECONDS);
        Assert.assertTrue("Speed test callback should finish within 4s", finished);
        Assert.assertTrue("Callback flag should be true", callbackTriggered.get());
        Assert.assertNotNull("Latency should be set", testItem.getLatencyMs());
    }

    @Test
    public void testOpenVpnTransportDetectionTcpAndUdp() {
        PresetRegionManager manager = PresetRegionManager.getInstance();
        manager.clear();

        String ovpnTcpConfig =
                "client\n" +
                "dev tun\n" +
                "proto tcp\n" +
                "remote 203.0.113.5 443\n" +
                "resolv-retry infinite\n" +
                "<ca>\n---BEGIN CERTIFICATE---\nfake\n---END CERTIFICATE---\n</ca>\n";

        ProxyProfile profileTcp = new ProxyProfile("Tokyo TCP", ProtocolType.OPENVPN, ovpnTcpConfig);
        profileTcp.setId("ovpn-tcp");
        PresetRegion regionTcp = PresetRegion.fromProfile(profileTcp);

        Assert.assertEquals(1, regionTcp.getPorts().size());
        Assert.assertEquals("TCP", regionTcp.getPorts().get(0).getTransport());
        Assert.assertEquals(443, regionTcp.getPorts().get(0).getPort());

        String ovpnUdpConfig =
                "client\n" +
                "dev tun\n" +
                "proto udp\n" +
                "remote 203.0.113.5 1194\n" +
                "resolv-retry infinite\n" +
                "<ca>\n---BEGIN CERTIFICATE---\nfake\n---END CERTIFICATE---\n</ca>\n";

        ProxyProfile profileUdp = new ProxyProfile("Tokyo UDP", ProtocolType.OPENVPN, ovpnUdpConfig);
        profileUdp.setId("ovpn-udp");
        PresetRegion regionUdp = PresetRegion.fromProfile(profileUdp);

        Assert.assertEquals(1, regionUdp.getPorts().size());
        Assert.assertEquals("UDP", regionUdp.getPorts().get(0).getTransport());
        Assert.assertEquals(1194, regionUdp.getPorts().get(0).getPort());
    }

    @Test
    public void testDedicatedChineseNameAndSiliconValley() {
        Assert.assertEquals("美国 · 硅谷", RegionDetector.detect("silicon valley 198.51.100.77").getDedicatedName());
        Assert.assertEquals("澳大利亚 · 墨尔本", RegionDetector.detect("mel-sample.example.com").getDedicatedName());
        Assert.assertEquals("日本 · 东京", RegionDetector.detect("tokyo-node").getDedicatedName());
        Assert.assertEquals("新加坡", RegionDetector.detect("singapore-node").getDedicatedName());
        Assert.assertEquals("德国 · 法兰克福", RegionDetector.detect("frankfurt-node").getDedicatedName());
        Assert.assertEquals("中国 · 香港", RegionDetector.detect("hong-kong-node").getDedicatedName());

        Assert.assertEquals("澳大利亚 · 墨尔本",
                RegionDetector.normalizeDisplayName("澳大利亚 · 墨尔本亚拉专线"));
        Assert.assertEquals("日本 · 东京",
                RegionDetector.normalizeDisplayName("日本 · 东京 CN2 GIA 专线.conf"));
        Assert.assertEquals("美国 · 硅谷",
                RegionDetector.normalizeDisplayName("美国 · 硅谷高速穿透节点"));
        Assert.assertEquals("新加坡",
                RegionDetector.normalizeDisplayName("新加坡 · 狮城骨干网 02"));
        Assert.assertEquals("中国 · 香港",
                RegionDetector.normalizeDisplayName("中国 · 香港 PCCW 直连"));

        PresetPortItem item = new PresetPortItem(
                "au-1", "澳大利亚 · 墨尔本", "🇦🇺",
                ProtocolType.WIREGUARD, "UDP", 1194,
                "mel-sample.example.com", "203.0.113.10",
                "node-au-test", "澳大利亚 · 墨尔本亚拉专线"
        );
        Assert.assertEquals("澳大利亚 · 墨尔本", item.getEffectiveChineseName());

        String wgConfig =
                "[Interface]\n" +
                "PrivateKey = aGVsbG93b3JsZHByaXZhdGVrZXlleGFtcGxlMTIzNDU2Nzg=\n" +
                "Address = 10.0.0.5/32\n" +
                "[Peer]\n" +
                "PublicKey = d2lyZWd1YXJkcHVibGlja2V5ZXhhbXBsZTEyMzQ1Njc4OTA=\n" +
                "Endpoint = mel-sample.example.com:1194\n" +
                "AllowedIPs = 0.0.0.0/0\n";
        ProxyProfile profile = new ProxyProfile(
                "澳大利亚 · 墨尔本亚拉专线", ProtocolType.WIREGUARD, wgConfig
        );
        profile.setId("node-au-test");
        PresetRegion region = PresetRegion.fromProfile(profile);
        Assert.assertNotNull(region);
        Assert.assertEquals("澳大利亚 · 墨尔本", region.getDedicatedName());
        Assert.assertFalse(region.getPorts().isEmpty());
        Assert.assertEquals("澳大利亚 · 墨尔本", region.getPorts().get(0).getEffectiveChineseName());
    }

    @Test
    public void testBundledNodes200Detection() {
        java.io.File assetsDir = new java.io.File("src/main/assets/bundled_nodes");
        if (!assetsDir.exists()) {
            assetsDir = new java.io.File("app/src/main/assets/bundled_nodes");
        }
        Assert.assertTrue("bundled_nodes assets dir must exist", assetsDir.exists());
        java.io.File[] files = assetsDir.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".conf"));
        Assert.assertNotNull(files);
        Assert.assertEquals("Must have 200 bundled conf files", 200, files.length);

        for (java.io.File f : files) {
            String name = f.getName();
            String endpoint = "";
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(f.toPath());
                for (String line : lines) {
                    if (line.trim().startsWith("Endpoint")) {
                        endpoint = line.split("=", 2)[1].trim();
                        break;
                    }
                }
            } catch (Exception e) {
                Assert.fail("Failed to read " + name + ": " + e.getMessage());
            }

            RegionDetector.RegionInfo info = RegionDetector.detect(name, endpoint);
            Assert.assertNotEquals("Node " + name + " must not fallback to global name", "全球代理节点", info.getName());
            Assert.assertNotEquals("Node " + name + " must not fallback to global flag", "🌐", info.getFlag());
            Assert.assertNotEquals("Node " + name + " must not have global country code", "global", info.getCountryCode());
        }

        // Spot checks
        RegionDetector.RegionInfo paris = RegionDetector.detect("Windscribe-Paris-Seine-WG.conf", "cdg-103-wg.vpn-example.net:443");
        Assert.assertEquals("🇫🇷", paris.getFlag());
        Assert.assertEquals("法国 · 巴黎", paris.getName());

        RegionDetector.RegionInfo zurich = RegionDetector.detect("Windscribe-Zurich-Alphorn-WG.conf", "zrh-112-wg.vpn-example.net:443");
        Assert.assertEquals("🇨🇭", zurich.getFlag());
        Assert.assertEquals("瑞士 · 苏黎世", zurich.getName());

        RegionDetector.RegionInfo ams = RegionDetector.detect("Windscribe-Amsterdam-Tulip-WG.conf", "ams-193-wg.vpn-example.net:443");
        Assert.assertEquals("🇳🇱", ams.getFlag());
        Assert.assertEquals("荷兰 · 阿姆斯特丹", ams.getName());

        RegionDetector.RegionInfo southBend = RegionDetector.detect("Windscribe-South-Bend-Hawkins-WG.conf", "sbn-388-wg.vpn-example.net:443");
        Assert.assertEquals("🇺🇸", southBend.getFlag());
        Assert.assertEquals("美国 · 南本德", southBend.getName());

        RegionDetector.RegionInfo bend = RegionDetector.detect("Windscribe-Bend-Oregon-Trail-WG.conf", "pdx-388-wg.vpn-example.net:443");
        Assert.assertEquals("🇺🇸", bend.getFlag());
        Assert.assertEquals("美国 · 本德", bend.getName());

        RegionDetector.RegionInfo wash = RegionDetector.detect("Windscribe-Washington-DC-Trade-War-WG.conf", "was-112-wg.vpn-example.net:443");
        Assert.assertEquals("🇺🇸", wash.getFlag());
        Assert.assertEquals("美国 · 华盛顿", wash.getName());

        RegionDetector.RegionInfo tokyo = RegionDetector.detect("Windscribe-Tokyo-Shinkansen-WG.conf", "nrt-120-wg.vpn-example.net:443");
        Assert.assertEquals("🇯🇵", tokyo.getFlag());
        Assert.assertEquals("日本 · 东京", tokyo.getName());

        RegionDetector.RegionInfo syd = RegionDetector.detect("Windscribe-Sydney-Eshay-WG.conf", "syd-101-wg.vpn-example.net:443");
        Assert.assertEquals("🇦🇺", syd.getFlag());
        Assert.assertEquals("澳大利亚 · 悉尼", syd.getName());
    }

    @Test
    public void testAll200BundledNodesUniquenessAndSubRegion() {
        java.io.File assetsDir = new java.io.File("src/main/assets/bundled_nodes");
        if (!assetsDir.exists()) {
            assetsDir = new java.io.File("app/src/main/assets/bundled_nodes");
        }
        Assert.assertTrue("bundled_nodes assets dir must exist", assetsDir.exists());
        java.io.File[] files = assetsDir.listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".conf"));
        Assert.assertNotNull(files);
        Assert.assertEquals("Must have 200 bundled conf files", 200, files.length);

        java.util.Map<String, java.util.Set<String>> countryToNodeNames = new java.util.HashMap<>();
        int subRegionExtractedCount = 0;

        for (java.io.File f : files) {
            String filename = f.getName();
            String profileName = filename.replaceAll("(?i)\\.conf$", "");
            String endpoint = "";
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(f.toPath());
                for (String line : lines) {
                    if (line.trim().startsWith("Endpoint")) {
                        endpoint = line.split("=", 2)[1].trim();
                        break;
                    }
                }
            } catch (Exception e) {
                Assert.fail("Failed to read " + filename + ": " + e.getMessage());
            }

            RegionDetector.RegionInfo regInfo = RegionDetector.detect(profileName, endpoint);
            String country = RegionDetector.getCountryNameByCode(regInfo.getCountryCode());
            if (country == null) country = regInfo.getName();

            PresetPortItem item = new PresetPortItem(
                    "id_" + profileName,
                    regInfo.getName(),
                    regInfo.getFlag(),
                    ProtocolType.WIREGUARD,
                    "UDP",
                    443,
                    endpoint,
                    "1.1.1.1",
                    "prof_" + profileName,
                    regInfo.getDedicatedName()
            );
            item.setRawProfileName(profileName);

            String displayName = MainActivity.getCityOrNodeName(item, null);
            Assert.assertNotNull("Display name must not be null for " + filename, displayName);
            Assert.assertFalse("Display name must not be empty for " + filename, displayName.trim().isEmpty());

            if (displayName.contains(" · ")) {
                subRegionExtractedCount++;
            }

            countryToNodeNames.putIfAbsent(country, new java.util.HashSet<>());
            boolean added = countryToNodeNames.get(country).add(displayName);
            Assert.assertTrue("Duplicate node name detected in country " + country + ": " + displayName + " (from " + filename + ")", added);
        }

        // 199 nodes have distinct subregions (Troll Station is single node in Antarctica)
        Assert.assertTrue("At least 199 bundled nodes must have detailed subregions", subRegionExtractedCount >= 199);
    }

    @Test
    public void testPresetPortItemSetPort() {
        PresetPortItem item = new PresetPortItem("jp-tokyo", "日本 · 东京", "🇯🇵",
                ProtocolType.WIREGUARD, "UDP", 51820, "jp.example.com", "1.2.3.4", "prof-1");
        Assert.assertEquals(51820, item.getPort());
        item.setPort(443);
        Assert.assertEquals(443, item.getPort());
        item.setPort(80);
        Assert.assertEquals(80, item.getPort());
        item.setPort(53);
        Assert.assertEquals(53, item.getPort());
        item.setPort(123);
        Assert.assertEquals(123, item.getPort());
        item.setPort(1194);
        Assert.assertEquals(1194, item.getPort());
        item.setPort(65142);
        Assert.assertEquals(65142, item.getPort());
    }

    @Test
    public void testAll72CountryNamesByCode() {
        String[] countryCodes = {
            "ae", "al", "aq", "ar", "at", "au", "ba", "be", "bg", "br",
            "ca", "ch", "cl", "co", "cy", "cz", "de", "dk", "ec", "ee",
            "es", "fi", "fr", "gb", "ge", "gr", "gt", "hk", "hr", "hu",
            "id", "ie", "il", "in", "is", "it", "jp", "ke", "kr", "lt",
            "lu", "lv", "md", "mk", "mx", "my", "ng", "nl", "no", "nz",
            "pa", "pe", "ph", "pl", "pt", "py", "ro", "rs", "ru", "se",
            "sg", "si", "sk", "sv", "th", "tr", "tw", "ua", "us", "uy",
            "vn", "za"
        };
        for (String code : countryCodes) {
            String cName = RegionDetector.getCountryNameByCode(code);
            Assert.assertNotNull("Country name for " + code + " must not be null", cName);
            Assert.assertFalse("Country name for " + code + " must not be empty", cName.trim().isEmpty());
        }
        Assert.assertEquals("英国", RegionDetector.getCountryNameByCode("uk"));
        Assert.assertEquals("英国", RegionDetector.getCountryNameByCode("gb"));
        Assert.assertEquals("中国香港", RegionDetector.getCountryNameByCode("hk"));
        Assert.assertEquals("中国台湾", RegionDetector.getCountryNameByCode("tw"));
        Assert.assertEquals("美国", RegionDetector.getCountryNameByCode("us"));
        Assert.assertEquals("日本", RegionDetector.getCountryNameByCode("jp"));
    }

    @Test
    public void testCountryAccordionSorting() {
        java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.CHINESE);

        List<String> countries = new ArrayList<>(java.util.Arrays.asList(
                "日本", "美国", "澳大利亚", "德国", "中国香港", "加拿大"
        ));
        Collections.sort(countries, (a, b) -> collator.compare(a, b));

        // Pinyin: 澳大利亚 (A) should be first
        Assert.assertEquals("澳大利亚", countries.get(0));

        // Test latency sorting with tie breaking
        class Node {
            final String name;
            final Long latency;
            Node(String name, Long latency) { this.name = name; this.latency = latency; }
        }
        List<Node> nodes = new ArrayList<>(java.util.Arrays.asList(
                new Node("东京", 120L),
                new Node("大阪", 35L),
                new Node("京都", null),
                new Node("名古屋", -1L)
        ));
        Collections.sort(nodes, (a, b) -> {
            long latA = a.latency != null && a.latency > 0 ? a.latency : Long.MAX_VALUE;
            long latB = b.latency != null && b.latency > 0 ? b.latency : Long.MAX_VALUE;
            int cmp = Long.compare(latA, latB);
            return cmp != 0 ? cmp : collator.compare(a.name, b.name);
        });

        Assert.assertEquals("大阪", nodes.get(0).name);
        Assert.assertEquals("东京", nodes.get(1).name);
    }

    @Test
    public void testDetailedSubRegionAndCityNameParsing() {
        // Sydney subregions
        String subSydney1 = MainActivity.extractSubRegionFromRaw("Windscribe-Sydney-Eshay-WG.conf", "悉尼");
        Assert.assertEquals("Eshay", subSydney1);
        String subSydney2 = MainActivity.extractSubRegionFromRaw("Windscribe-Sydney-Squidney-WG", "悉尼");
        Assert.assertEquals("Squidney", subSydney2);
        Assert.assertNotEquals(subSydney1, subSydney2);

        // Paris subregions
        String subParis1 = MainActivity.extractSubRegionFromRaw("Windscribe-Paris-Jardin-WG.conf", "巴黎");
        Assert.assertEquals("Jardin", subParis1);
        String subParis2 = MainActivity.extractSubRegionFromRaw("Windscribe-Paris-Seine-WG", "巴黎");
        Assert.assertEquals("Seine", subParis2);
        Assert.assertNotEquals(subParis1, subParis2);

        // Los Angeles subregions (multi-word city)
        String subLa1 = MainActivity.extractSubRegionFromRaw("Windscribe-Los-Angeles-Cube-WG", "洛杉矶");
        Assert.assertEquals("Cube", subLa1);
        String subLa2 = MainActivity.extractSubRegionFromRaw("Windscribe-Los-Angeles-Dogg-WG.conf", "洛杉矶");
        Assert.assertEquals("Dogg", subLa2);
        Assert.assertNotEquals(subLa1, subLa2);

        // New York multi-word subregion
        String subNy = MainActivity.extractSubRegionFromRaw("Windscribe-New-York-Grand-Central-WG.conf", "纽约");
        Assert.assertEquals("Grand Central", subNy);

        // Buenos Aires multi-word city & subregion
        String subBa = MainActivity.extractSubRegionFromRaw("Windscribe-Buenos-Aires-Yerba-Mate-WG.conf", "布宜诺斯艾利斯");
        Assert.assertEquals("Yerba Mate", subBa);

        // Tokyo subregion & node without subregion
        String subTokyo = MainActivity.extractSubRegionFromRaw("Windscribe-Tokyo-Shinkansen-WG.conf", "东京");
        Assert.assertEquals("Shinkansen", subTokyo);
        String noSubTokyo = MainActivity.extractSubRegionFromRaw("Windscribe-Tokyo-WG.conf", "东京");
        Assert.assertNull(noSubTokyo);

        // Numeric aliases preserved (should not be stripped as ports)
        String subLondon1984 = MainActivity.extractSubRegionFromRaw("Windscribe-London-1984-WG.conf", "伦敦");
        Assert.assertEquals("1984", subLondon1984);
        String subJoburg = MainActivity.extractSubRegionFromRaw("Windscribe-Johannesburg-District-69-WG.conf", "约翰内斯堡");
        Assert.assertEquals("District 69", subJoburg);
        String subMontreal = MainActivity.extractSubRegionFromRaw("Windscribe-Montreal-Expo-67-WG.conf", "蒙特利尔");
        Assert.assertEquals("Expo 67", subMontreal);
        String subToronto = MainActivity.extractSubRegionFromRaw("Windscribe-Toronto-The-6-WG.conf", "多伦多");
        Assert.assertEquals("The 6", subToronto);

        // PresetPortItem rawProfileName preservation & getCityOrNodeName
        PresetPortItem item1 = new PresetPortItem("id1", "澳大利亚 · 悉尼", "🇦🇺", ProtocolType.WIREGUARD, "UDP", 443, "1.1.1.1", "1.1.1.1");
        item1.setRawProfileName("Windscribe-Sydney-Eshay-WG");
        Assert.assertEquals("Windscribe-Sydney-Eshay-WG", item1.getRawProfileName());
        Assert.assertEquals("悉尼 · Eshay", MainActivity.getCityOrNodeName(item1, null));

        PresetPortItem itemLondon = new PresetPortItem("id2", "英国 · 伦敦", "🇬🇧", ProtocolType.WIREGUARD, "UDP", 443, "1.1.1.1", "1.1.1.1");
        itemLondon.setRawProfileName("Windscribe-London-1984-WG.conf");
        Assert.assertEquals("伦敦 · 1984", MainActivity.getCityOrNodeName(itemLondon, null));
    }

    @Test
    public void testTrafficStatsManagerFormatBytes() {
        Assert.assertEquals("0 B", TrafficStatsManager.formatBytes(-50));
        Assert.assertEquals("0 B", TrafficStatsManager.formatBytes(0));
        Assert.assertEquals("512 B", TrafficStatsManager.formatBytes(512));
        Assert.assertEquals("1023 B", TrafficStatsManager.formatBytes(1023));
        Assert.assertEquals("1.00 KB", TrafficStatsManager.formatBytes(1024));
        Assert.assertEquals("1.50 KB", TrafficStatsManager.formatBytes(1536));
        Assert.assertEquals("1.00 MB", TrafficStatsManager.formatBytes(1024 * 1024L));
        Assert.assertEquals("10.00 MB", TrafficStatsManager.formatBytes(10 * 1024 * 1024L));
        Assert.assertEquals("1.00 GB", TrafficStatsManager.formatBytes(1024 * 1024 * 1024L));
        Assert.assertEquals("2.50 GB", TrafficStatsManager.formatBytes((long)(2.5 * 1024 * 1024 * 1024L)));
        Assert.assertEquals("1.00 TB", TrafficStatsManager.formatBytes(1024L * 1024 * 1024 * 1024L));
    }

    public static void runAllTests() {
        System.out.println("--- Running PresetRegionTest ---");
        PresetRegionTest test = new PresetRegionTest();
        try {
            test.testPresetRegionManagerInitialState();
            System.out.println("  ✓ testPresetRegionManagerInitialState passed");
            test.testDetailedSubRegionAndCityNameParsing();
            System.out.println("  ✓ testDetailedSubRegionAndCityNameParsing passed");
            test.testRegionDetector();
            System.out.println("  ✓ testRegionDetector passed");
            test.testAll72FlagDrawables();
            System.out.println("  ✓ testAll72FlagDrawables passed (72/72 countries verified)");
            test.testAll72CountryNamesByCode();
            System.out.println("  ✓ testAll72CountryNamesByCode passed (72/72 countries verified)");
            test.testCountryAccordionSorting();
            System.out.println("  ✓ testCountryAccordionSorting passed");
            test.testWireGuardNativePort();
            System.out.println("  ✓ testWireGuardNativePort passed");
            test.testOpenVpnNativePort();
            System.out.println("  ✓ testOpenVpnNativePort passed");
            test.testProfilePortPurityPreservedWireGuard();
            System.out.println("  ✓ testProfilePortPurityPreservedWireGuard passed");
            test.testProfilePortPurityPreservedOpenVpn();
            System.out.println("  ✓ testProfilePortPurityPreservedOpenVpn passed");
            test.testSyncFromProfiles();
            System.out.println("  ✓ testSyncFromProfiles passed");
            test.testPortSpeedTesterAsync();
            System.out.println("  ✓ testPortSpeedTesterAsync passed");
            test.testOpenVpnTransportDetectionTcpAndUdp();
            System.out.println("  ✓ testOpenVpnTransportDetectionTcpAndUdp passed");
            test.testDedicatedChineseNameAndSiliconValley();
            System.out.println("  ✓ testDedicatedChineseNameAndSiliconValley passed");
            test.testBundledNodes200Detection();
            System.out.println("  ✓ testBundledNodes200Detection passed (200/200 nodes)");
            test.testAll200BundledNodesUniquenessAndSubRegion();
            System.out.println("  ✓ testAll200BundledNodesUniquenessAndSubRegion passed (200/200 unique names)");
            test.testPresetPortItemSetPort();
            System.out.println("  ✓ testPresetPortItemSetPort passed");
            test.testTrafficStatsManagerFormatBytes();
            System.out.println("  ✓ testTrafficStatsManagerFormatBytes passed");
            System.out.println("PresetRegionTest: ALL PASSED!\n");
        } catch (Exception e) {
            System.err.println("PresetRegionTest FAILED: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
