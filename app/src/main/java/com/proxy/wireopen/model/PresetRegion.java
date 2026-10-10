package com.proxy.wireopen.model;

import com.proxy.wireopen.util.RegionDetector;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a geographical region node derived from an imported proxy profile,
 * containing accelerated ports strictly corresponding to the profile's protocol.
 */
public class PresetRegion implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final String englishName;
    private final String flag;
    private final String host;
    private final String ip;
    private final ProtocolType protocolType;
    private final String profileId;
    private final String dedicatedName;
    private boolean isExpanded = true;
    private final List<PresetPortItem> ports = new ArrayList<>();

    public PresetRegion(String id, String name, String englishName, String flag, String host, String ip) {
        this(id, name, englishName, flag, host, ip, ProtocolType.WIREGUARD, null, null);
    }

    public PresetRegion(String id, String name, String englishName, String flag, String host, String ip,
                        ProtocolType protocolType, String profileId) {
        this(id, name, englishName, flag, host, ip, protocolType, profileId, null);
    }

    public PresetRegion(String id, String name, String englishName, String flag, String host, String ip,
                        ProtocolType protocolType, String profileId, String dedicatedName) {
        this(id, name, englishName, flag, host, ip, protocolType, profileId, dedicatedName,
                (protocolType == ProtocolType.OPENVPN ? 1194 : 51820), "UDP");
    }

    public PresetRegion(String id, String name, String englishName, String flag, String host, String ip,
                        ProtocolType protocolType, String profileId, String dedicatedName, int port, String transport) {
        this.id = id;
        this.name = name;
        this.englishName = englishName;
        this.flag = flag;
        this.host = host;
        this.ip = ip;
        this.protocolType = protocolType;
        this.profileId = profileId;
        this.dedicatedName = (dedicatedName != null && !dedicatedName.trim().isEmpty())
                ? dedicatedName
                : RegionDetector.detectDedicatedChineseName(englishName, name, host);
        this.ports.clear();
        PresetPortItem portItem = new PresetPortItem(id, name, flag, protocolType, transport, port, host, ip, profileId, this.dedicatedName);
        portItem.setRawProfileName(englishName);
        this.ports.add(portItem);
    }

    /**
     * Constructs a PresetRegion directly from an imported ProxyProfile,
     * maintaining its native IP, port, and protocol without deriving synthetic ports.
     */
    public static PresetRegion fromProfile(ProxyProfile profile) {
        if (profile == null) return null;

        String endpointHost = "";
        int port = 51820;
        String transport = "UDP";
        String ip = "";

        if (profile.getProtocolType() == ProtocolType.WIREGUARD) {
            if (profile.getWireGuardConfig() == null && profile.getRawConfig() != null) {
                try {
                    profile.setWireGuardConfig(com.proxy.wireopen.parser.WireGuardConfigParser.parse(profile.getRawConfig()));
                } catch (Exception ignored) {}
            }
            if (profile.getWireGuardConfig() != null) {
                endpointHost = profile.getWireGuardConfig().getEndpointHost();
                if (profile.getWireGuardConfig().getEndpointPort() > 0) {
                    port = profile.getWireGuardConfig().getEndpointPort();
                }
            }
            transport = "UDP";
        } else if (profile.getProtocolType() == ProtocolType.OPENVPN) {
            port = 1194;
            if (profile.getOpenVpnConfig() == null && profile.getRawConfig() != null) {
                try {
                    profile.setOpenVpnConfig(com.proxy.wireopen.parser.OpenVpnConfigParser.parse(profile.getRawConfig()));
                } catch (Exception ignored) {}
            }
            if (profile.getOpenVpnConfig() != null) {
                endpointHost = profile.getOpenVpnConfig().getRemoteHost();
                if (profile.getOpenVpnConfig().getRemotePort() > 0) {
                    port = profile.getOpenVpnConfig().getRemotePort();
                }
                if (profile.getOpenVpnConfig().getProtocol() != null && !profile.getOpenVpnConfig().getProtocol().trim().isEmpty()) {
                    String p = profile.getOpenVpnConfig().getProtocol().trim().toUpperCase(java.util.Locale.ROOT);
                    transport = p.contains("TCP") ? "TCP" : "UDP";
                }
            }
        }

        if (endpointHost == null || endpointHost.isEmpty()) {
            endpointHost = "127.0.0.1";
        }

        RegionDetector.RegionInfo regionInfo = RegionDetector.detect(
                profile.getName(),
                endpointHost,
                profile.getEndpointDisplay(),
                profile.getRawConfig()
        );

        return new PresetRegion(
                profile.getId(),
                regionInfo.getName(),
                profile.getName(),
                regionInfo.getFlag(),
                endpointHost,
                ip,
                profile.getProtocolType(),
                profile.getId(),
                regionInfo.getDedicatedName(),
                port,
                transport
        );
    }

    public String getDedicatedName() { return dedicatedName; }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEnglishName() { return englishName; }
    public String getFlag() { return flag; }
    public String getHost() { return host; }
    public String getIp() { return ip; }
    public ProtocolType getProtocolType() { return protocolType; }
    public String getProfileId() { return profileId; }

    public boolean isExpanded() { return isExpanded; }
    public void setExpanded(boolean expanded) { isExpanded = expanded; }

    public List<PresetPortItem> getPorts() { return ports; }

    public List<PresetPortItem> getPortsByGroup(ProtocolType type, String transport) {
        List<PresetPortItem> filtered = new ArrayList<>();
        for (PresetPortItem item : ports) {
            if (item.getProtocolType() == type && item.getTransport().equalsIgnoreCase(transport)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public String getCountryCode() {
        return com.proxy.wireopen.util.RegionDetector.detect(name, englishName, host, ip).getCountryCode();
    }
}
