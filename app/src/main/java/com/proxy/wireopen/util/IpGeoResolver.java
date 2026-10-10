package com.proxy.wireopen.util;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Resolves an endpoint hostname to an IP and retrieves geographical metadata.
 * IP-based lookup is preferred, with a second provider and explicit hostname
 * city hints as fallbacks for unreliable/blocked GeoIP responses.
 */
public final class IpGeoResolver {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private IpGeoResolver() {}

    public interface Callback {
        void onResolved(GeoInfo info);
        void onError(Exception error);
    }

    public static final class GeoInfo {
        private final String ip;
        private final String countryCode;
        private final String country;
        private final String region;
        private final String city;
        private final String displayNameOverride;

        public GeoInfo(String ip, String countryCode, String country, String region, String city) {
            this(ip, countryCode, country, region, city, "");
        }

        public GeoInfo(String ip, String countryCode, String country, String region,
                       String city, String displayNameOverride) {
            this.ip = ip;
            this.countryCode = countryCode != null ? countryCode : "";
            this.country = country != null ? country : "";
            this.region = region != null ? region : "";
            this.city = city != null ? city : "";
            this.displayNameOverride = displayNameOverride != null ? displayNameOverride : "";
        }

        public String getIp() { return ip; }
        public String getCountryCode() { return countryCode; }
        public String getCountry() { return country; }
        public String getRegion() { return region; }
        public String getCity() { return city; }

        public String getDisplayName() {
            if (!displayNameOverride.isEmpty()) {
                return displayNameOverride;
            }

            String countryCn = toChineseCountry(countryCode, country);
            String cityCn = toChineseCity(city);
            String regionCn = toChineseRegion(countryCode, region);

            // Prefer the human-facing country + city format. This keeps names
            // compact and matches the node list design, e.g. 澳大利亚 · 墨尔本.
            if (!cityCn.isEmpty()) {
                if ("HK".equalsIgnoreCase(countryCode)) {
                    return "中国 · 香港";
                }
                if ("TW".equalsIgnoreCase(countryCode)) {
                    return "中国 · 台湾" + (cityCn.equals("台北") ? "" : " · " + cityCn);
                }
                return countryCn + " · " + cityCn;
            }

            StringBuilder result = new StringBuilder(countryCn);
            if (!regionCn.isEmpty() && !regionCn.equals(countryCn)) {
                result.append(" · ").append(regionCn);
            }
            return result.toString();
        }

        private static String toChineseCountry(String code, String fallback) {
            if (code == null || code.isEmpty()) {
                return fallback == null || fallback.isEmpty() ? "未知地区" : fallback;
            }
            switch (code.toUpperCase()) {
                case "US": return "美国";
                case "FR": return "法国";
                case "DE": return "德国";
                case "GB": return "英国";
                case "JP": return "日本";
                case "KR": return "韩国";
                case "SG": return "新加坡";
                case "AU": return "澳大利亚";
                case "CA": return "加拿大";
                case "NL": return "荷兰";
                case "HK": return "中国香港";
                case "TW": return "中国台湾";
                case "CN": return "中国";
                default:
                    return fallback == null || fallback.isEmpty() ? "未知地区" : fallback;
            }
        }

        private static String toChineseRegion(String code, String value) {
            if (value == null || value.isEmpty()) return "";
            if ("US".equalsIgnoreCase(code)) {
                if ("California".equalsIgnoreCase(value)) return "加利福尼亚州";
                if ("New York".equalsIgnoreCase(value)) return "纽约州";
                if ("Texas".equalsIgnoreCase(value)) return "得克萨斯州";
                if ("Washington".equalsIgnoreCase(value)) return "华盛顿州";
            }
            if ("AU".equalsIgnoreCase(code)) {
                if ("Victoria".equalsIgnoreCase(value)) return "维多利亚州";
                if ("New South Wales".equalsIgnoreCase(value)) return "新南威尔士州";
                if ("Queensland".equalsIgnoreCase(value)) return "昆士兰州";
                if ("Western Australia".equalsIgnoreCase(value)) return "西澳大利亚州";
                if ("South Australia".equalsIgnoreCase(value)) return "南澳大利亚州";
                if ("Tasmania".equalsIgnoreCase(value)) return "塔斯马尼亚州";
                if ("Northern Territory".equalsIgnoreCase(value)) return "北领地";
                if ("Australian Capital Territory".equalsIgnoreCase(value)) return "澳大利亚首都领地";
            }
            return value;
        }

        private static String toChineseCity(String value) {
            if (value == null || value.isEmpty()) return "";
            switch (value.toLowerCase()) {
                case "san francisco": return "旧金山";
                case "san jose": return "圣何塞";
                case "los angeles": return "洛杉矶";
                case "new york": return "纽约";
                case "seattle": return "西雅图";
                case "chicago": return "芝加哥";
                case "dallas": return "达拉斯";
                case "paris": return "巴黎";
                case "frankfurt": return "法兰克福";
                case "london": return "伦敦";
                case "tokyo": return "东京";
                case "osaka": return "大阪";
                case "seoul": return "首尔";
                case "singapore": return "新加坡";
                case "hong kong": return "香港";
                case "taipei": return "台北";
                case "sydney": return "悉尼";
                case "melbourne": return "墨尔本";
                case "amsterdam": return "阿姆斯特丹";
                default: return value;
            }
        }
    }

    public static void resolveAsync(String host, Callback callback) {
        new Thread(() -> {
            try {
                GeoInfo info = resolve(host);
                if (callback != null) callback.onResolved(info);
            } catch (Exception e) {
                if (callback != null) callback.onError(e);
            }
        }, "IpGeoResolver").start();
    }

    public static GeoInfo resolve(String host) throws Exception {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("节点地址为空");
        }

        String inputHost = host.trim();
        InetAddress address = InetAddress.getByName(inputHost);
        String ip = address.getHostAddress();
        if (ip == null || ip.isEmpty()) {
            throw new IllegalArgumentException("无法解析节点 IP");
        }

        Exception firstError = null;
        try {
            GeoInfo info = queryIpWho(ip);
            return info;
        } catch (Exception e) {
            firstError = e;
        }

        try {
            GeoInfo info = queryIpApi(ip);
            return info;
        } catch (Exception secondError) {
            if (firstError != null) {
                secondError.addSuppressed(firstError);
            }
            throw secondError;
        }
    }

    private static GeoInfo queryIpWho(String ip) throws Exception {
        JSONObject json = getJson("https://ipwho.is/" + ip);
        if (!json.optBoolean("success", true)) {
            throw new IllegalStateException(json.optString("message", "ipwho.is 查询失败"));
        }
        return new GeoInfo(
                ip,
                json.optString("country_code", ""),
                json.optString("country", ""),
                json.optString("region", ""),
                json.optString("city", "")
        );
    }

    private static GeoInfo queryIpApi(String ip) throws Exception {
        JSONObject json = getJson("https://ipapi.co/" + ip + "/json/");
        if (json.has("error") && json.optBoolean("error", false)) {
            throw new IllegalStateException(json.optString("reason", "ipapi.co 查询失败"));
        }
        return new GeoInfo(
                ip,
                json.optString("country_code", ""),
                json.optString("country_name", ""),
                json.optString("region", ""),
                json.optString("city", "")
        );
    }

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("GeoIP HTTP " + code);
            }
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                return new JSONObject(body.toString());
            }
        } finally {
            connection.disconnect();
        }
    }

    private static GeoInfo applyStrongHostnameHint(String host, GeoInfo geo) {
        RegionDetector.RegionInfo hint = RegionDetector.detect(host);
        if ("global".equalsIgnoreCase(hint.getCountryCode()) || !hasStrongCityHint(host)) {
            return geo;
        }

        // Explicit city-bearing hostnames are useful as a last-mile naming signal
        // when GeoIP databases disagree. Keep the raw IP fields intact but override
        // the user-facing region label.
        if (!hint.getName().equalsIgnoreCase(geo.getDisplayName())) {
            return new GeoInfo(
                    geo.getIp(),
                    geo.getCountryCode(),
                    geo.getCountry(),
                    geo.getRegion(),
                    geo.getCity(),
                    hint.getName()
            );
        }
        return geo;
    }

    private static boolean hasStrongCityHint(String host) {
        String s = host != null ? host.toLowerCase() : "";
        return s.contains("melbourne") || s.contains("mel-")
                || s.contains("sydney")
                || s.contains("tokyo") || s.contains("osaka")
                || s.contains("frankfurt")
                || s.contains("london")
                || s.contains("seoul")
                || s.contains("singapore")
                || s.contains("hongkong") || s.contains("hong-kong")
                || s.contains("taipei")
                || s.contains("sanfrancisco") || s.contains("san-francisco")
                || s.contains("losangeles") || s.contains("los-angeles")
                || s.contains("sfo") || s.contains("lax");
    }
}
