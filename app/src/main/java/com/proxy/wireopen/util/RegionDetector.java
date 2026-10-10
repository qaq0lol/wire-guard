package com.proxy.wireopen.util;

import java.io.Serializable;
import java.util.Locale;

/**
 * Intelligent detector that automatically identifies geographical region and flag emoji
 * from profile filename, profile name, endpoint host, or configuration text.
 */
public class RegionDetector {

    public static class RegionInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String flag;
        private final String name;
        private final String countryCode;
        private final String dedicatedName;

        public RegionInfo(String flag, String name, String countryCode) {
            this(flag, name, countryCode, name);
        }

        public RegionInfo(String flag, String name, String countryCode, String dedicatedName) {
            this.flag = flag;
            this.name = name;
            this.countryCode = countryCode;
            this.dedicatedName = normalizeDisplayName(
                    dedicatedName != null && !dedicatedName.trim().isEmpty() ? dedicatedName : name
            );
        }

        public String getFlag() { return flag; }
        public String getName() { return name; }
        public String getCountryCode() { return countryCode; }
        public String getDedicatedName() {
            return normalizeDisplayName(dedicatedName != null ? dedicatedName : name);
        }

        @Override
        public String toString() {
            return flag + " " + name;
        }
    }

    /**
     * Removes legacy provider/marketing suffixes from a node name.
     * This is intentionally applied at the final display boundary as well,
     * so old saved profiles are cleaned without touching their raw configs.
     */
    public static String normalizeDisplayName(String value) {
        if (value == null) return "";
        String normalized = value.trim();

        normalized = normalized.replaceAll("(?i)\\.(?:conf|ovpn)$", "").trim();

        String[] suffixPatterns = {
                "CN2\\s*GIA\\s*专线",
                "Cera\\s*旗舰专线",
                "高速穿透节点",
                "亚拉专线",
                "Yarra(?:\\s*专线)?",
                "狮城骨干网(?:\\s*0?2)?",
                "核心骨干(?:网)?",
                "PCCW\\s*直连",
                "海底光缆专线",
                "高速专线",
                "HiNet\\s*专线"
        };

        boolean changed;
        do {
            changed = false;
            for (String suffix : suffixPatterns) {
                String cleaned = normalized.replaceAll(
                        "(?i)\\s*(?:" + suffix + ")\\s*$", ""
                ).trim();
                if (!cleaned.equals(normalized)) {
                    normalized = cleaned;
                    changed = true;
                }
            }
        } while (changed && !normalized.isEmpty());

        normalized = normalized.replaceAll("[\\s·•|/_-]+$", "").trim();
        return normalized;
    }

    private static class Rule {
        final String flag;
        final String name;
        final String countryCode;
        final String dedicatedName;
        final String[] keywords;
        final String[] iatas;

        Rule(String flag, String name, String countryCode, String dedicatedName, String[] keywords, String[] iatas) {
            this.flag = flag;
            this.name = name;
            this.countryCode = countryCode;
            this.dedicatedName = dedicatedName;
            this.keywords = keywords;
            this.iatas = iatas;
        }
    }

    private static final Rule[] RULES = new Rule[] {
        // --- East Asia ---
        new Rule("🇭🇰", "中国 · 香港", "hk", "中国 · 香港",
                new String[]{"hong kong", "hong-kong", "hongkong", "香港", "hk", "hkg", ".hk.", ".hk:", ".com.hk", "🇭🇰"},
                new String[]{"hkg"}),
        new Rule("🇹🇼", "中国 · 台湾", "tw", "中国 · 台湾",
                new String[]{"taiwan", "taipei", "台湾", "台北", "tw", "twn", ".tw.", ".tw:", ".com.tw", "🇹🇼"},
                new String[]{"tpe"}),
        new Rule("🇯🇵", "日本 · 东京", "jp", "日本 · 东京",
                new String[]{"tokyo", "东京"},
                new String[]{"hnd", "nrt"}),
        new Rule("🇯🇵", "日本 · 大阪", "jp", "日本 · 大阪",
                new String[]{"osaka", "大阪"},
                new String[]{"kix"}),
        new Rule("🇯🇵", "日本", "jp", "日本",
                new String[]{"japan", "日本", "jp", "jpn", ".jp.", ".jp:", ".co.jp", "🇯🇵"},
                new String[]{"jp"}),
        new Rule("🇰🇷", "韩国 · 首尔", "kr", "韩国 · 首尔",
                new String[]{"seoul", "首尔", "korea", "韩国", "kr", "kor", ".kr.", ".kr:", ".co.kr", "🇰🇷"},
                new String[]{"icn"}),

        // --- Southeast Asia ---
        new Rule("🇸🇬", "新加坡", "sg", "新加坡",
                new String[]{"singapore", "新加坡", "狮城", "sg", "sgp", ".sg.", ".sg:", ".com.sg", "🇸🇬"},
                new String[]{"sin"}),
        new Rule("🇹🇭", "泰国 · 曼谷", "th", "泰国 · 曼谷",
                new String[]{"bangkok", "曼谷", "thailand", "泰国", "🇹🇭"},
                new String[]{"bkk"}),
        new Rule("🇻🇳", "越南 · 河内", "vn", "越南 · 河内",
                new String[]{"hanoi", "河内", "vietnam", "越南", "🇻🇳"},
                new String[]{"han"}),
        new Rule("🇲🇾", "马来西亚 · 吉隆坡", "my", "马来西亚 · 吉隆坡",
                new String[]{"kuala lumpur", "kuala-lumpur", "吉隆坡", "malaysia", "马来西亚", "🇲🇾"},
                new String[]{"kul"}),
        new Rule("🇮🇩", "印度尼西亚 · 巴厘岛", "id", "印度尼西亚 · 巴厘岛",
                new String[]{"bali", "巴厘岛"},
                new String[]{"dps"}),
        new Rule("🇮🇩", "印度尼西亚 · 泗水", "id", "印度尼西亚 · 泗水",
                new String[]{"surabaya", "泗水"},
                new String[]{"sub"}),
        new Rule("🇮🇩", "印度尼西亚 · 雅加达", "id", "印度尼西亚 · 雅加达",
                new String[]{"jakarta", "雅加达", "indonesia", "印度尼西亚", "🇮🇩"},
                new String[]{"cgk"}),
        new Rule("🇵🇭", "菲律宾 · 马尼拉", "ph", "菲律宾 · 马尼拉",
                new String[]{"manila", "马尼拉", "philippines", "菲律宾", "🇵🇭"},
                new String[]{"mnl"}),

        // --- South Asia ---
        new Rule("🇮🇳", "印度 · 孟买", "in", "印度 · 孟买",
                new String[]{"fake-mumbai", "mumbai", "bombay", "孟买"},
                new String[]{"bom"}),
        new Rule("🇮🇳", "印度 · 新德里", "in", "印度 · 新德里",
                new String[]{"new delhi", "new-delhi", "delhi", "新德里", "德里", "india", "印度", "🇮🇳"},
                new String[]{"del"}),

        // --- Middle East ---
        new Rule("🇦🇪", "阿联酋 · 迪拜", "ae", "阿联酋 · 迪拜",
                new String[]{"dubai", "迪拜", "uae", "阿联酋", "🇦🇪"},
                new String[]{"dxb"}),
        new Rule("🇮🇱", "以色列 · 阿什杜德", "il", "以色列 · 阿什杜德",
                new String[]{"ashdod", "阿什杜德"},
                new String[]{}),
        new Rule("🇮🇱", "以色列 · 特拉维夫", "il", "以色列 · 特拉维夫",
                new String[]{"tel aviv", "tel-aviv", "特拉维夫", "israel", "以色列", "🇮🇱"},
                new String[]{"tlv"}),
        new Rule("🇹🇷", "土耳其 · 伊斯坦布尔", "tr", "土耳其 · 伊斯坦布尔",
                new String[]{"istanbul", "伊斯坦布尔", "turkey", "土耳其", "🇹🇷"},
                new String[]{"ist"}),
        new Rule("🇨🇾", "塞浦路斯 · 利马索尔", "cy", "塞浦路斯 · 利马索尔",
                new String[]{"limassol", "利马索尔"},
                new String[]{"qli"}),
        new Rule("🇨🇾", "塞浦路斯 · 尼科西亚", "cy", "塞浦路斯 · 尼科西亚",
                new String[]{"nicosia", "尼科西亚", "cyprus", "塞浦路斯", "🇨🇾"},
                new String[]{"lca"}),
        new Rule("🇬🇪", "格鲁吉亚 · 第比利斯", "ge", "格鲁吉亚 · 第比利斯",
                new String[]{"tbilisi", "第比利斯", "georgia", "格鲁吉亚", "🇬🇪"},
                new String[]{"tbs"}),

        // --- Oceania ---
        new Rule("🇦🇺", "澳大利亚 · 阿德莱德", "au", "澳大利亚 · 阿德莱德",
                new String[]{"adelaide", "阿德莱德"},
                new String[]{"adl"}),
        new Rule("🇦🇺", "澳大利亚 · 布里斯班", "au", "澳大利亚 · 布里斯班",
                new String[]{"brisbane", "布里斯班"},
                new String[]{"bne"}),
        new Rule("🇦🇺", "澳大利亚 · 墨尔本", "au", "澳大利亚 · 墨尔本",
                new String[]{"melbourne", "墨尔本", "wg1194-melbourne"},
                new String[]{"mel"}),
        new Rule("🇦🇺", "澳大利亚 · 珀斯", "au", "澳大利亚 · 珀斯",
                new String[]{"perth", "珀斯"},
                new String[]{"per"}),
        new Rule("🇦🇺", "澳大利亚 · 悉尼", "au", "澳大利亚 · 悉尼",
                new String[]{"sydney", "悉尼"},
                new String[]{"syd"}),
        new Rule("🇦🇺", "澳大利亚 · 墨尔本", "au", "澳大利亚 · 墨尔本",
                new String[]{"australia", "澳大利亚", ".au.", ".au:", ".com.au", "🇦🇺"},
                new String[]{"au", "aus"}),
        new Rule("🇳🇿", "新西兰 · 奥克兰", "nz", "新西兰 · 奥克兰",
                new String[]{"auckland", "奥克兰", "new zealand", "新西兰", "🇳🇿"},
                new String[]{"akl"}),

        // --- Europe - Western & Central ---
        new Rule("🇬🇧", "英国 · 曼彻斯特", "gb", "英国 · 曼彻斯特",
                new String[]{"manchester", "shamchester", "曼彻斯特"},
                new String[]{"man"}),
        new Rule("🇬🇧", "英国 · 爱丁堡", "gb", "英国 · 爱丁堡",
                new String[]{"edinburgh", "爱丁堡"},
                new String[]{"edi"}),
        new Rule("🇬🇧", "英国 · 伦敦", "gb", "英国 · 伦敦",
                new String[]{"london", "伦敦", "united kingdom", "great britain", "英国", ".uk.", ".uk:", ".co.uk", "🇬🇧"},
                new String[]{"lhr", "uk", "gb", "gbr"}),
        new Rule("🇫🇷", "法国 · 波尔多", "fr", "法国 · 波尔多",
                new String[]{"bordeaux", "波尔多"},
                new String[]{"bod"}),
        new Rule("🇫🇷", "法国 · 马赛", "fr", "法国 · 马赛",
                new String[]{"marseille", "马赛"},
                new String[]{"mrs"}),
        new Rule("🇫🇷", "法国 · 巴黎", "fr", "法国 · 巴黎",
                new String[]{"paris", "巴黎", "france", "法国", "🇫🇷"},
                new String[]{"cdg"}),
        new Rule("🇩🇪", "德国 · 柏林", "de", "德国 · 柏林",
                new String[]{"berlin", "柏林"},
                new String[]{"ber"}),
        new Rule("🇩🇪", "德国 · 法兰克福", "de", "德国 · 法兰克福",
                new String[]{"frankfurt", "法兰克福", "germany", "deutschland", "德国", ".de.", ".de:", "🇩🇪"},
                new String[]{"fra", "de", "deu"}),
        new Rule("🇳🇱", "荷兰 · 阿姆斯特丹", "nl", "荷兰 · 阿姆斯特丹",
                new String[]{"amsterdam", "阿姆斯特丹", "netherlands", "荷兰", "🇳🇱"},
                new String[]{"ams", "nl", "nld"}),
        new Rule("🇧🇪", "比利时 · 布鲁塞尔", "be", "比利时 · 布鲁塞尔",
                new String[]{"brussels", "bruxelles", "布鲁塞尔", "belgium", "比利时", "🇧🇪"},
                new String[]{"bru", "bel"}),
        new Rule("🇨🇭", "瑞士 · 苏黎世", "ch", "瑞士 · 苏黎世",
                new String[]{"zurich", "zürich", "苏黎世", "switzerland", "瑞士", "🇨🇭"},
                new String[]{"zrh", "che"}),
        new Rule("🇦🇹", "奥地利 · 维也纳", "at", "奥地利 · 维也纳",
                new String[]{"vienna", "wien", "维也纳", "austria", "奥地利", "🇦🇹"},
                new String[]{"vie", "aut"}),
        new Rule("🇮🇪", "爱尔兰 · 都柏林", "ie", "爱尔兰 · 都柏林",
                new String[]{"dublin", "都柏林", "ireland", "爱尔兰", "🇮🇪"},
                new String[]{"dub", "irl"}),
        new Rule("🇱🇺", "卢森堡 · 卢森堡", "lu", "卢森堡 · 卢森堡",
                new String[]{"luxembourg", "卢森堡", "🇱🇺"},
                new String[]{"lux"}),

        // --- Europe - Southern ---
        new Rule("🇮🇹", "意大利 · 巴勒莫", "it", "意大利 · 巴勒莫",
                new String[]{"palermo", "巴勒莫"},
                new String[]{"pmo"}),
        new Rule("🇮🇹", "意大利 · 米兰", "it", "意大利 · 米兰",
                new String[]{"milan", "milano", "米兰", "italy", "italia", "意大利", "🇮🇹"},
                new String[]{"mxp", "ita"}),
        new Rule("🇪🇸", "西班牙 · 巴塞罗那", "es", "西班牙 · 巴塞罗那",
                new String[]{"barcelona", "巴塞罗那"},
                new String[]{"bcn"}),
        new Rule("🇪🇸", "西班牙 · 马德里", "es", "西班牙 · 马德里",
                new String[]{"madrid", "马德里", "spain", "españa", "西班牙", "🇪🇸"},
                new String[]{"mad", "esp"}),
        new Rule("🇵🇹", "葡萄牙 · 里斯本", "pt", "葡萄牙 · 里斯本",
                new String[]{"lisbon", "lisboa", "里斯本", "portugal", "葡萄牙", "🇵🇹"},
                new String[]{"lis", "prt"}),
        new Rule("🇬🇷", "希腊 · 雅典", "gr", "希腊 · 雅典",
                new String[]{"athens", "雅典", "greece", "希腊", "🇬🇷"},
                new String[]{"ath", "grc"}),

        // --- Europe - Northern ---
        new Rule("🇸🇪", "瑞典 · 斯德哥尔摩", "se", "瑞典 · 斯德哥尔摩",
                new String[]{"stockholm", "斯德哥尔摩", "sweden", "瑞典", "🇸🇪"},
                new String[]{"arn", "swe"}),
        new Rule("🇳🇴", "挪威 · 奥斯陆", "no", "挪威 · 奥斯陆",
                new String[]{"oslo", "奥斯陆", "norway", "挪威", "🇳🇴"},
                new String[]{"osl", "nor"}),
        new Rule("🇩🇰", "丹麦 · 哥本哈根", "dk", "丹麦 · 哥本哈根",
                new String[]{"copenhagen", "københavn", "哥本哈根", "denmark", "丹麦", "🇩🇰"},
                new String[]{"cph", "dnk"}),
        new Rule("🇫🇮", "芬兰 · 赫尔辛基", "fi", "芬兰 · 赫尔辛基",
                new String[]{"helsinki", "赫尔辛基", "finland", "芬兰", "🇫🇮"},
                new String[]{"hel", "fin"}),
        new Rule("🇮🇸", "冰岛 · 雷克雅未克", "is", "冰岛 · 雷克雅未克",
                new String[]{"reykjavik", "雷克雅未克", "iceland", "冰岛", "🇮🇸"},
                new String[]{"kef", "isl"}),

        // --- Europe - Eastern & Baltic & Balkan ---
        new Rule("🇵🇱", "波兰 · 格但斯克", "pl", "波兰 · 格但斯克",
                new String[]{"gdansk", "gdańsk", "格但斯克"},
                new String[]{}),
        new Rule("🇵🇱", "波兰 · 华沙", "pl", "波兰 · 华沙",
                new String[]{"warsaw", "warszawa", "华沙", "poland", "polska", "波兰", "🇵🇱"},
                new String[]{"waw", "pol"}),
        new Rule("🇨🇿", "捷克 · 布拉格", "cz", "捷克 · 布拉格",
                new String[]{"prague", "praha", "布拉格", "czech", "捷克", "🇨🇿"},
                new String[]{"prg", "cze"}),
        new Rule("🇸🇰", "斯洛伐克 · 布拉迪斯拉发", "sk", "斯洛伐克 · 布拉迪斯拉发",
                new String[]{"bratislava", "布拉迪斯拉发", "slovakia", "斯洛伐克", "🇸🇰"},
                new String[]{"bts", "svk"}),
        new Rule("🇭🇺", "匈牙利 · 布达佩斯", "hu", "匈牙利 · 布达佩斯",
                new String[]{"budapest", "布达佩斯", "hungary", "magyar", "匈牙利", "🇭🇺"},
                new String[]{"bud", "hun"}),
        new Rule("🇷🇴", "罗马尼亚 · 布加勒斯特", "ro", "罗马尼亚 · 布加勒斯特",
                new String[]{"bucharest", "bucuresti", "布加勒斯特", "romania", "罗马尼亚", "🇷🇴"},
                new String[]{"otp", "rou"}),
        new Rule("🇧🇬", "保加利亚 · 索非亚", "bg", "保加利亚 · 索非亚",
                new String[]{"sofia", "索非亚", "索菲亚", "bulgaria", "保加利亚", "🇧🇬"},
                new String[]{"sof", "bgr"}),
        new Rule("🇭🇷", "克罗地亚 · 萨格勒布", "hr", "克罗地亚 · 萨格勒布",
                new String[]{"zagreb", "萨格勒布", "croatia", "克罗地亚", "🇭🇷"},
                new String[]{"zag", "hrv"}),
        new Rule("🇷🇸", "塞尔维亚 · 贝尔格莱德", "rs", "塞尔维亚 · 贝尔格莱德",
                new String[]{"belgrade", "beograd", "贝尔格莱德", "serbia", "塞尔维亚", "🇷🇸"},
                new String[]{"beg", "srb"}),
        new Rule("🇸🇮", "斯洛文尼亚 · 卢布尔雅那", "si", "斯洛文尼亚 · 卢布尔雅那",
                new String[]{"ljubljana", "卢布尔雅那", "slovenia", "斯洛文尼亚", "🇸🇮"},
                new String[]{"lju", "svn"}),
        new Rule("🇧🇦", "波黑 · 新特拉夫尼克", "ba", "波黑 · 新特拉夫尼克",
                new String[]{"novi travnik", "novi-travnik", "新特拉夫尼克"},
                new String[]{}),
        new Rule("🇧🇦", "波黑 · 萨拉热窝", "ba", "波黑 · 萨拉热窝",
                new String[]{"sarajevo", "萨拉热窝", "bosnia", "波黑", "🇧🇦"},
                new String[]{"sjj", "bih"}),
        new Rule("🇲🇰", "北马其顿 · 斯科普里", "mk", "北马其顿 · 斯科普里",
                new String[]{"skopje", "斯科普里", "macedonia", "北马其顿", "🇲🇰"},
                new String[]{"skp", "mkd"}),
        new Rule("🇦🇱", "阿尔巴尼亚 · 地拉那", "al", "阿尔巴尼亚 · 地拉那",
                new String[]{"tirana", "地拉那", "albania", "阿尔巴尼亚", "🇦🇱"},
                new String[]{"tia", "alb"}),
        new Rule("🇪🇪", "爱沙尼亚 · 塔林", "ee", "爱沙尼亚 · 塔林",
                new String[]{"tallinn", "塔林", "estonia", "爱沙尼亚", "🇪🇪"},
                new String[]{"tll", "est"}),
        new Rule("🇱🇻", "拉脱维亚 · 里加", "lv", "拉脱维亚 · 里加",
                new String[]{"riga", "里加", "latvia", "拉脱维亚", "🇱🇻"},
                new String[]{"rix", "lva"}),
        new Rule("🇱🇹", "立陶宛 · 维尔纽斯", "lt", "立陶宛 · 维尔纽斯",
                new String[]{"vilnius", "维尔纽斯", "lithuania", "立陶宛", "🇱🇹"},
                new String[]{"vno", "ltu"}),
        new Rule("🇲🇩", "摩尔多瓦 · 基希讷乌", "md", "摩尔多瓦 · 基希讷乌",
                new String[]{"chisinau", "chișinău", "基希讷乌", "moldova", "摩尔多瓦", "🇲🇩"},
                new String[]{"kiv", "mda"}),
        new Rule("🇺🇦", "乌克兰 · 基辅", "ua", "乌克兰 · 基辅",
                new String[]{"kyiv", "kiev", "基辅", "ukraine", "乌克兰", "🇺🇦"},
                new String[]{"kbp", "ukr"}),
        new Rule("🇷🇺", "俄罗斯 · 圣彼得堡", "ru", "俄罗斯 · 圣彼得堡",
                new String[]{"st-petersburg", "st. petersburg", "saint petersburg", "圣彼得堡"},
                new String[]{"led"}),
        new Rule("🇷🇺", "俄罗斯 · 莫斯科", "ru", "俄罗斯 · 莫斯科",
                new String[]{"moscow", "莫斯科", "russia", "俄罗斯", "🇷🇺"},
                new String[]{"svo", "rus"}),

        // --- North America - Canada ---
        new Rule("🇨🇦", "加拿大 · 卡尔加里", "ca", "加拿大 · 卡尔加里",
                new String[]{"calgary", "卡尔加里"},
                new String[]{"yyc"}),
        new Rule("🇨🇦", "加拿大 · 哈利法克斯", "ca", "加拿大 · 哈利法克斯",
                new String[]{"halifax", "哈利法克斯"},
                new String[]{"yhz"}),
        new Rule("🇨🇦", "加拿大 · 蒙特利尔", "ca", "加拿大 · 蒙特利尔",
                new String[]{"montreal", "montréal", "蒙特利尔"},
                new String[]{"yul"}),
        new Rule("🇨🇦", "加拿大 · 温哥华", "ca", "加拿大 · 温哥华",
                new String[]{"vancouver", "温哥华"},
                new String[]{"yvr"}),
        new Rule("🇨🇦", "加拿大 · 温尼伯", "ca", "加拿大 · 温尼伯",
                new String[]{"winnipeg", "温尼伯"},
                new String[]{"ywg"}),
        new Rule("🇨🇦", "加拿大 · 多伦多", "ca", "加拿大 · 多伦多",
                new String[]{"toronto", "多伦多", "canada", "加拿大", "🇨🇦"},
                new String[]{"yyz", "can"}),

        // --- North America - US Cities ---
        new Rule("🇺🇸", "美国 · 盐湖城", "us", "美国 · 盐湖城",
                new String[]{"salt lake city", "salt-lake-city", "盐湖城"},
                new String[]{"slc"}),
        new Rule("🇺🇸", "美国 · 旧金山", "us", "美国 · 旧金山",
                new String[]{"san francisco", "san-francisco", "旧金山"},
                new String[]{"sfo"}),
        new Rule("🇺🇸", "美国 · 硅谷", "us", "美国 · 硅谷",
                new String[]{"silicon valley", "silicon", "硅谷"},
                new String[]{}),
        new Rule("🇺🇸", "美国 · 圣何塞", "us", "美国 · 圣何塞",
                new String[]{"san jose", "san-jose", "圣何塞"},
                new String[]{}),
        new Rule("🇺🇸", "美国 · 圣克拉拉", "us", "美国 · 圣克拉拉",
                new String[]{"santa clara", "santa-clara", "圣克拉拉"},
                new String[]{"sjc"}),
        new Rule("🇺🇸", "美国 · 堪萨斯城", "us", "美国 · 堪萨斯城",
                new String[]{"kansas city", "kansas-city", "堪萨斯城"},
                new String[]{"mci"}),
        new Rule("🇺🇸", "美国 · 拉斯维加斯", "us", "美国 · 拉斯维加斯",
                new String[]{"las vegas", "las-vegas", "拉斯维加斯"},
                new String[]{"las"}),
        new Rule("🇺🇸", "美国 · 华盛顿", "us", "美国 · 华盛顿",
                new String[]{"washington dc", "washington-dc", "washington", "华盛顿"},
                new String[]{"was"}),
        new Rule("🇺🇸", "美国 · 新泽西", "us", "美国 · 新泽西",
                new String[]{"new jersey", "new-jersey", "新泽西"},
                new String[]{"ewr"}),
        new Rule("🇺🇸", "美国 · 纽约", "us", "美国 · 纽约",
                new String[]{"new york", "new-york", "纽约"},
                new String[]{"jfk"}),
        new Rule("🇺🇸", "美国 · 芝加哥", "us", "美国 · 芝加哥",
                new String[]{"chicago", "芝加哥"},
                new String[]{"ord"}),
        new Rule("🇺🇸", "美国 · 洛杉矶", "us", "美国 · 洛杉矶",
                new String[]{"los angeles", "los-angeles", "洛杉矶"},
                new String[]{"lax"}),
        new Rule("🇺🇸", "美国 · 亚特兰大", "us", "美国 · 亚特兰大",
                new String[]{"atlanta", "亚特兰大", "亚特兰大市"},
                new String[]{"atl"}),
        new Rule("🇺🇸", "美国 · 波士顿", "us", "美国 · 波士顿",
                new String[]{"boston", "波士顿"},
                new String[]{"bos"}),
        new Rule("🇺🇸", "美国 · 达拉斯", "us", "美国 · 达拉斯",
                new String[]{"dallas", "达拉斯"},
                new String[]{"dfw"}),
        new Rule("🇺🇸", "美国 · 丹佛", "us", "美国 · 丹佛",
                new String[]{"denver", "丹佛"},
                new String[]{"den"}),
        new Rule("🇺🇸", "美国 · 底特律", "us", "美国 · 底特律",
                new String[]{"detroit", "底特律"},
                new String[]{"dtw"}),
        new Rule("🇺🇸", "美国 · 休斯顿", "us", "美国 · 休斯顿",
                new String[]{"houston", "休斯顿"},
                new String[]{"iah"}),
        new Rule("🇺🇸", "美国 · 迈阿密", "us", "美国 · 迈阿密",
                new String[]{"miami", "迈阿密"},
                new String[]{"mia"}),
        new Rule("🇺🇸", "美国 · 费城", "us", "美国 · 费城",
                new String[]{"philadelphia", "费城"},
                new String[]{"phl"}),
        new Rule("🇺🇸", "美国 · 菲尼克斯", "us", "美国 · 菲尼克斯",
                new String[]{"phoenix", "菲尼克斯"},
                new String[]{"phx"}),
        new Rule("🇺🇸", "美国 · 西雅图", "us", "美国 · 西雅图",
                new String[]{"seattle", "西雅图"},
                new String[]{"sea"}),
        new Rule("🇺🇸", "美国 · 坦帕", "us", "美国 · 坦帕",
                new String[]{"tampa", "坦帕"},
                new String[]{"tpa"}),
        new Rule("🇺🇸", "美国 · 阿什本", "us", "美国 · 阿什本",
                new String[]{"ashburn", "阿什本"},
                new String[]{"iad"}),
        new Rule("🇺🇸", "美国 · 夏洛特", "us", "美国 · 夏洛特",
                new String[]{"charlotte", "夏洛特"},
                new String[]{"clt"}),
        new Rule("🇺🇸", "美国 · 克利夫兰", "us", "美国 · 克利夫兰",
                new String[]{"cleveland", "克利夫兰"},
                new String[]{"cle"}),
        new Rule("🇺🇸", "美国 · 罗利", "us", "美国 · 罗利",
                new String[]{"raleigh", "罗利"},
                new String[]{"rdu"}),
        new Rule("🇺🇸", "美国 · 南本德", "us", "美国 · 南本德",
                new String[]{"south bend", "south-bend", "南本德"},
                new String[]{"sbn"}),
        new Rule("🇺🇸", "美国 · 本德", "us", "美国 · 本德",
                new String[]{"bend", "本德"},
                new String[]{"pdx"}),
        new Rule("🇺🇸", "美国 · 水牛城", "us", "美国 · 水牛城",
                new String[]{"buffalo", "水牛城"},
                new String[]{"buf"}),
        new Rule("🇺🇸", "美国 · 檀香山", "us", "美国 · 檀香山",
                new String[]{"honolulu", "檀香山"},
                new String[]{"hnl"}),
        new Rule("🇺🇸", "美国 · 洛杉矶", "us", "美国 · 洛杉矶",
                new String[]{"cera", "america", "us-cera", "united states", "california", "美国", ".us.", ".us:", "🇺🇸"},
                new String[]{"us", "usa"}),

        // --- Latin America & Caribbean ---
        new Rule("🇧🇷", "巴西 · 圣保罗", "br", "巴西 · 圣保罗",
                new String[]{"sao paulo", "sao-paulo", "são paulo", "圣保罗", "brazil", "brasil", "巴西", "🇧🇷"},
                new String[]{"gru", "bra"}),
        new Rule("🇦🇷", "阿根廷 · 布宜诺斯艾利斯", "ar", "阿根廷 · 布宜诺斯艾利斯",
                new String[]{"buenos aires", "buenos-aires", "布宜诺斯艾利斯", "argentina", "阿根廷", "🇦🇷"},
                new String[]{"eze", "arg"}),
        new Rule("🇨🇱", "智利 · 圣地亚哥", "cl", "智利 · 圣地亚哥",
                new String[]{"santiago", "圣地亚哥", "chile", "智利", "🇨🇱"},
                new String[]{"scl", "chl"}),
        new Rule("🇨🇴", "哥伦比亚 · 波哥大", "co", "哥伦比亚 · 波哥大",
                new String[]{"bogota", "bogotá", "波哥大", "colombia", "哥伦比亚", "🇨🇴"},
                new String[]{"bog", "col"}),
        new Rule("🇲🇽", "墨西哥 · 克雷塔罗", "mx", "墨西哥 · 克雷塔罗",
                new String[]{"queretaro", "querétaro", "克雷塔罗", "mexico", "méxico", "墨西哥", "🇲🇽"},
                new String[]{"qro", "mex"}),
        new Rule("🇵🇪", "秘鲁 · 利马", "pe", "秘鲁 · 利马",
                new String[]{"lima", "利马", "peru", "perú", "秘鲁", "🇵🇪"},
                new String[]{"lim"}),
        new Rule("🇪🇨", "厄瓜多尔 · 基多", "ec", "厄瓜多尔 · 基多",
                new String[]{"quito", "基多", "ecuador", "厄瓜多尔", "🇪🇨"},
                new String[]{"uio", "ecu"}),
        new Rule("🇺🇾", "乌拉圭 · 蒙得维的亚", "uy", "乌拉圭 · 蒙得维的亚",
                new String[]{"montevideo", "蒙得维的亚", "uruguay", "乌拉圭", "🇺🇾"},
                new String[]{"mvd", "ury"}),
        new Rule("🇵🇾", "巴拉圭 · 亚松森", "py", "巴拉圭 · 亚松森",
                new String[]{"asuncion", "asunción", "亚松森", "paraguay", "巴拉圭", "🇵🇾"},
                new String[]{"asu", "pry"}),
        new Rule("🇵🇦", "巴拿马 · 巴拿马城", "pa", "巴拿马 · 巴拿马城",
                new String[]{"panama", "panama-city", "巴拿马", "🇵🇦"},
                new String[]{"pty", "pan"}),
        new Rule("🇸🇻", "萨尔瓦多 · 圣萨尔瓦多", "sv", "萨尔瓦多 · 圣萨尔瓦多",
                new String[]{"san salvador", "san-salvador", "圣萨尔瓦多", "el salvador", "萨尔瓦多", "🇸🇻"},
                new String[]{"sal", "slv"}),
        new Rule("🇬🇹", "危地马拉 · 危地马拉城", "gt", "危地马拉 · 危地马拉城",
                new String[]{"guatemala", "guatemala-city", "危地马拉", "🇬🇹"},
                new String[]{"gua", "gtm"}),

        // --- Africa ---
        new Rule("🇿🇦", "南非 · 约翰内斯堡", "za", "南非 · 约翰内斯堡",
                new String[]{"johannesburg", "约翰内斯堡", "约堡", "south africa", "南非", "🇿🇦"},
                new String[]{"jnb", "zaf"}),
        new Rule("🇳🇬", "尼日利亚 · 拉各斯", "ng", "尼日利亚 · 拉各斯",
                new String[]{"lagos", "拉各斯", "nigeria", "尼日利亚", "🇳🇬"},
                new String[]{"los", "nga"}),
        new Rule("🇰🇪", "肯尼亚 · 内罗毕", "ke", "肯尼亚 · 内罗毕",
                new String[]{"nairobi", "内罗毕", "kenya", "肯尼亚", "🇰🇪"},
                new String[]{"nbo", "ken"}),

        // --- Antarctica ---
        new Rule("🇦🇶", "南极 · 特罗尔站", "aq", "南极 · 特罗尔站",
                new String[]{"troll station", "troll-station", "特罗尔", "antarctica", "南极", "🇦🇶"},
                new String[]{"wfr", "ata"})
    };

    private static boolean isChinese(String str) {
        if (str == null) return false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fa5') return true;
        }
        return false;
    }

    private static boolean hasEmoji(String str) {
        if (str == null) return false;
        for (int i = 0; i < str.length(); i++) {
            int codePoint = str.codePointAt(i);
            if (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF) return true;
        }
        return false;
    }

    /**
     * Detects geographical information from human-readable profile names,
     * filenames, hostnames and config text. Exact hostname/city hints are
     * preferred; unreliable generic IP-prefix guesses are deliberately avoided.
     */
    public static RegionInfo detect(String... searchTexts) {
        StringBuilder combined = new StringBuilder();
        if (searchTexts != null) {
            for (String text : searchTexts) {
                if (text != null) combined.append(" ").append(text.toLowerCase(Locale.ROOT));
            }
        }
        String s = combined.toString();

        java.util.Set<String> tokens = new java.util.HashSet<>();
        if (searchTexts != null) {
            for (String text : searchTexts) {
                if (text != null) {
                    for (String part : text.toLowerCase(Locale.ROOT)
                            .split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
                        if (!part.isEmpty()) tokens.add(part);
                    }
                }
            }
        }

        for (Rule rule : RULES) {
            boolean matched = false;
            for (String kw : rule.keywords) {
                String kwLower = kw.toLowerCase(Locale.ROOT);
                if ((kwLower.contains(" ") || kwLower.contains("-") || kwLower.contains(".") || kwLower.contains(":"))
                        && s.contains(kwLower)) {
                    matched = true;
                    break;
                } else if (tokens.contains(kwLower)) {
                    matched = true;
                    break;
                } else if (kwLower.length() >= 2 && isChinese(kwLower) && s.contains(kwLower)) {
                    matched = true;
                    break;
                } else if (hasEmoji(kw) && s.contains(kw)) {
                    matched = true;
                    break;
                }
            }
            if (!matched && rule.iatas != null) {
                for (String iata : rule.iatas) {
                    if (tokens.contains(iata.toLowerCase(Locale.ROOT))) {
                        matched = true;
                        break;
                    }
                }
            }
            if (matched) {
                return new RegionInfo(rule.flag, rule.name, rule.countryCode, rule.dedicatedName);
            }
        }

        return new RegionInfo("🌐", "全球代理节点", "global", "全球代理节点");
    }

    public static String detectFlag(String... searchTexts) {
        return detect(searchTexts).getFlag();
    }

    public static String detectRegionName(String... searchTexts) {
        return detect(searchTexts).getName();
    }

    public static String detectDedicatedChineseName(String... searchTexts) {
        return normalizeDisplayName(detect(searchTexts).getDedicatedName());
    }

    public static int getFlagDrawableRes(String countryCode) {
        if (countryCode == null) return com.proxy.wireopen.R.drawable.flag_global;
        switch (countryCode.toLowerCase(Locale.ROOT)) {
            case "ae": return com.proxy.wireopen.R.drawable.flag_ae;
            case "al": return com.proxy.wireopen.R.drawable.flag_al;
            case "aq": return com.proxy.wireopen.R.drawable.flag_aq;
            case "ar": return com.proxy.wireopen.R.drawable.flag_ar;
            case "at": return com.proxy.wireopen.R.drawable.flag_at;
            case "au": return com.proxy.wireopen.R.drawable.flag_au;
            case "ba": return com.proxy.wireopen.R.drawable.flag_ba;
            case "be": return com.proxy.wireopen.R.drawable.flag_be;
            case "bg": return com.proxy.wireopen.R.drawable.flag_bg;
            case "br": return com.proxy.wireopen.R.drawable.flag_br;
            case "ca": return com.proxy.wireopen.R.drawable.flag_ca;
            case "ch": return com.proxy.wireopen.R.drawable.flag_ch;
            case "cl": return com.proxy.wireopen.R.drawable.flag_cl;
            case "co": return com.proxy.wireopen.R.drawable.flag_co;
            case "cy": return com.proxy.wireopen.R.drawable.flag_cy;
            case "cz": return com.proxy.wireopen.R.drawable.flag_cz;
            case "de": return com.proxy.wireopen.R.drawable.flag_de;
            case "dk": return com.proxy.wireopen.R.drawable.flag_dk;
            case "ec": return com.proxy.wireopen.R.drawable.flag_ec;
            case "ee": return com.proxy.wireopen.R.drawable.flag_ee;
            case "es": return com.proxy.wireopen.R.drawable.flag_es;
            case "fi": return com.proxy.wireopen.R.drawable.flag_fi;
            case "fr": return com.proxy.wireopen.R.drawable.flag_fr;
            case "gb":
            case "uk": return com.proxy.wireopen.R.drawable.flag_gb;
            case "ge": return com.proxy.wireopen.R.drawable.flag_ge;
            case "gr": return com.proxy.wireopen.R.drawable.flag_gr;
            case "gt": return com.proxy.wireopen.R.drawable.flag_gt;
            case "hk": return com.proxy.wireopen.R.drawable.flag_hk;
            case "hr": return com.proxy.wireopen.R.drawable.flag_hr;
            case "hu": return com.proxy.wireopen.R.drawable.flag_hu;
            case "id": return com.proxy.wireopen.R.drawable.flag_id;
            case "ie": return com.proxy.wireopen.R.drawable.flag_ie;
            case "il": return com.proxy.wireopen.R.drawable.flag_il;
            case "in": return com.proxy.wireopen.R.drawable.flag_in;
            case "is": return com.proxy.wireopen.R.drawable.flag_is;
            case "it": return com.proxy.wireopen.R.drawable.flag_it;
            case "jp": return com.proxy.wireopen.R.drawable.flag_jp;
            case "ke": return com.proxy.wireopen.R.drawable.flag_ke;
            case "kr": return com.proxy.wireopen.R.drawable.flag_kr;
            case "lt": return com.proxy.wireopen.R.drawable.flag_lt;
            case "lu": return com.proxy.wireopen.R.drawable.flag_lu;
            case "lv": return com.proxy.wireopen.R.drawable.flag_lv;
            case "md": return com.proxy.wireopen.R.drawable.flag_md;
            case "mk": return com.proxy.wireopen.R.drawable.flag_mk;
            case "mx": return com.proxy.wireopen.R.drawable.flag_mx;
            case "my": return com.proxy.wireopen.R.drawable.flag_my;
            case "ng": return com.proxy.wireopen.R.drawable.flag_ng;
            case "nl": return com.proxy.wireopen.R.drawable.flag_nl;
            case "no": return com.proxy.wireopen.R.drawable.flag_no;
            case "nz": return com.proxy.wireopen.R.drawable.flag_nz;
            case "pa": return com.proxy.wireopen.R.drawable.flag_pa;
            case "pe": return com.proxy.wireopen.R.drawable.flag_pe;
            case "ph": return com.proxy.wireopen.R.drawable.flag_ph;
            case "pl": return com.proxy.wireopen.R.drawable.flag_pl;
            case "pt": return com.proxy.wireopen.R.drawable.flag_pt;
            case "py": return com.proxy.wireopen.R.drawable.flag_py;
            case "ro": return com.proxy.wireopen.R.drawable.flag_ro;
            case "rs": return com.proxy.wireopen.R.drawable.flag_rs;
            case "ru": return com.proxy.wireopen.R.drawable.flag_ru;
            case "se": return com.proxy.wireopen.R.drawable.flag_se;
            case "sg": return com.proxy.wireopen.R.drawable.flag_sg;
            case "si": return com.proxy.wireopen.R.drawable.flag_si;
            case "sk": return com.proxy.wireopen.R.drawable.flag_sk;
            case "sv": return com.proxy.wireopen.R.drawable.flag_sv;
            case "th": return com.proxy.wireopen.R.drawable.flag_th;
            case "tr": return com.proxy.wireopen.R.drawable.flag_tr;
            case "tw": return com.proxy.wireopen.R.drawable.flag_tw;
            case "ua": return com.proxy.wireopen.R.drawable.flag_ua;
            case "us": return com.proxy.wireopen.R.drawable.flag_us;
            case "uy": return com.proxy.wireopen.R.drawable.flag_uy;
            case "vn": return com.proxy.wireopen.R.drawable.flag_vn;
            case "za": return com.proxy.wireopen.R.drawable.flag_za;
            default: return com.proxy.wireopen.R.drawable.flag_global;
        }
    }

    public static String getCountryNameByCode(String countryCode) {
        if (countryCode == null) return null;
        switch (countryCode.toLowerCase(Locale.ROOT)) {
            case "ae": return "阿联酋";
            case "al": return "阿尔巴尼亚";
            case "aq": return "南极洲";
            case "ar": return "阿根廷";
            case "at": return "奥地利";
            case "au": return "澳大利亚";
            case "ba": return "波黑";
            case "be": return "比利时";
            case "bg": return "保加利亚";
            case "br": return "巴西";
            case "ca": return "加拿大";
            case "ch": return "瑞士";
            case "cl": return "智利";
            case "co": return "哥伦比亚";
            case "cy": return "塞浦路斯";
            case "cz": return "捷克";
            case "de": return "德国";
            case "dk": return "丹麦";
            case "ec": return "厄瓜多尔";
            case "ee": return "爱沙尼亚";
            case "es": return "西班牙";
            case "fi": return "芬兰";
            case "fr": return "法国";
            case "gb":
            case "uk": return "英国";
            case "ge": return "格鲁吉亚";
            case "gr": return "希腊";
            case "gt": return "危地马拉";
            case "hk": return "中国香港";
            case "hr": return "克罗地亚";
            case "hu": return "匈牙利";
            case "id": return "印度尼西亚";
            case "ie": return "爱尔兰";
            case "il": return "以色列";
            case "in": return "印度";
            case "is": return "冰岛";
            case "it": return "意大利";
            case "jp": return "日本";
            case "ke": return "肯尼亚";
            case "kr": return "韩国";
            case "lt": return "立陶宛";
            case "lu": return "卢森堡";
            case "lv": return "拉脱维亚";
            case "md": return "摩尔多瓦";
            case "mk": return "北马其顿";
            case "mx": return "墨西哥";
            case "my": return "马来西亚";
            case "ng": return "尼日利亚";
            case "nl": return "荷兰";
            case "no": return "挪威";
            case "nz": return "新西兰";
            case "pa": return "巴拿马";
            case "pe": return "秘鲁";
            case "ph": return "菲律宾";
            case "pl": return "波兰";
            case "pt": return "葡萄牙";
            case "py": return "巴拉圭";
            case "ro": return "罗马尼亚";
            case "rs": return "塞尔维亚";
            case "ru": return "俄罗斯";
            case "se": return "瑞典";
            case "sg": return "新加坡";
            case "si": return "斯洛文尼亚";
            case "sk": return "斯洛伐克";
            case "sv": return "萨尔瓦多";
            case "th": return "泰国";
            case "tr": return "土耳其";
            case "tw": return "中国台湾";
            case "ua": return "乌克兰";
            case "us": return "美国";
            case "uy": return "乌拉圭";
            case "vn": return "越南";
            case "za": return "南非";
            default: return null;
        }
    }

}

