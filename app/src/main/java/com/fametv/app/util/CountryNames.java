package com.fametv.app.util;

import java.util.HashMap;
import java.util.Map;

/**
 * ISO 3166-1 alpha-2 country code → Simplified Chinese name lookup.
 *
 * Used so that the country-grouped channel list can show both English and
 * Chinese names, e.g. "新加坡 Singapore" / "新加坡 · Singapore · eng".
 *
 * Covers all 169 countries that currently appear in the famelack/famelack-data
 * TV channel catalog. If a code is missing it falls back to the original
 * English name passed in.
 */
public final class CountryNames {

    private CountryNames() {}

    private static final Map<String, String> ZH = new HashMap<>();

    static {
        ZH.put("ad", "安道尔");
        ZH.put("ae", "阿联酋");
        ZH.put("af", "阿富汗");
        ZH.put("ag", "安提瓜和巴布达");
        ZH.put("al", "阿尔巴尼亚");
        ZH.put("am", "亚美尼亚");
        ZH.put("ao", "安哥拉");
        ZH.put("ar", "阿根廷");
        ZH.put("at", "奥地利");
        ZH.put("au", "澳大利亚");
        ZH.put("aw", "阿鲁巴");
        ZH.put("az", "阿塞拜疆");
        ZH.put("ba", "波黑");
        ZH.put("bd", "孟加拉国");
        ZH.put("be", "比利时");
        ZH.put("bf", "布基纳法索");
        ZH.put("bg", "保加利亚");
        ZH.put("bh", "巴林");
        ZH.put("bj", "贝宁");
        ZH.put("bo", "玻利维亚");
        ZH.put("bq", "荷兰加勒比");
        ZH.put("br", "巴西");
        ZH.put("bs", "巴哈马");
        ZH.put("by", "白俄罗斯");
        ZH.put("bz", "伯利兹");
        ZH.put("ca", "加拿大");
        ZH.put("cd", "刚果民主共和国");
        ZH.put("cg", "刚果共和国");
        ZH.put("ch", "瑞士");
        ZH.put("ci", "科特迪瓦");
        ZH.put("cl", "智利");
        ZH.put("cm", "喀麦隆");
        ZH.put("cn", "中国");
        ZH.put("co", "哥伦比亚");
        ZH.put("cr", "哥斯达黎加");
        ZH.put("cu", "古巴");
        ZH.put("cv", "佛得角");
        ZH.put("cy", "塞浦路斯");
        ZH.put("cz", "捷克");
        ZH.put("de", "德国");
        ZH.put("dj", "吉布提");
        ZH.put("dk", "丹麦");
        ZH.put("dm", "多米尼克");
        ZH.put("do", "多米尼加");
        ZH.put("dz", "阿尔及利亚");
        ZH.put("ec", "厄瓜多尔");
        ZH.put("ee", "爱沙尼亚");
        ZH.put("eg", "埃及");
        ZH.put("es", "西班牙");
        ZH.put("et", "埃塞俄比亚");
        ZH.put("fi", "芬兰");
        ZH.put("fj", "斐济");
        ZH.put("fm", "密克罗尼西亚");
        ZH.put("fo", "法罗群岛");
        ZH.put("fr", "法国");
        ZH.put("ga", "加蓬");
        ZH.put("gb", "英国");
        ZH.put("gd", "格林纳达");
        ZH.put("ge", "格鲁吉亚");
        ZH.put("gh", "加纳");
        ZH.put("gi", "直布罗陀");
        ZH.put("gm", "冈比亚");
        ZH.put("gn", "几内亚");
        ZH.put("gp", "瓜德罗普");
        ZH.put("gq", "赤道几内亚");
        ZH.put("gr", "希腊");
        ZH.put("gt", "危地马拉");
        ZH.put("gy", "圭亚那");
        ZH.put("hk", "香港");
        ZH.put("hn", "洪都拉斯");
        ZH.put("hr", "克罗地亚");
        ZH.put("ht", "海地");
        ZH.put("hu", "匈牙利");
        ZH.put("id", "印度尼西亚");
        ZH.put("ie", "爱尔兰");
        ZH.put("il", "以色列");
        ZH.put("in", "印度");
        ZH.put("iq", "伊拉克");
        ZH.put("ir", "伊朗");
        ZH.put("is", "冰岛");
        ZH.put("it", "意大利");
        ZH.put("jm", "牙买加");
        ZH.put("jo", "约旦");
        ZH.put("jp", "日本");
        ZH.put("ke", "肯尼亚");
        ZH.put("kg", "吉尔吉斯斯坦");
        ZH.put("kh", "柬埔寨");
        ZH.put("km", "科摩罗");
        ZH.put("kn", "圣基茨和尼维斯");
        ZH.put("kp", "朝鲜");
        ZH.put("kr", "韩国");
        ZH.put("kw", "科威特");
        ZH.put("kz", "哈萨克斯坦");
        ZH.put("la", "老挝");
        ZH.put("lb", "黎巴嫩");
        ZH.put("lc", "圣卢西亚");
        ZH.put("li", "列支敦士登");
        ZH.put("lk", "斯里兰卡");
        ZH.put("lr", "利比里亚");
        ZH.put("ls", "莱索托");
        ZH.put("lt", "立陶宛");
        ZH.put("lu", "卢森堡");
        ZH.put("lv", "拉脱维亚");
        ZH.put("ly", "利比亚");
        ZH.put("ma", "摩洛哥");
        ZH.put("mc", "摩纳哥");
        ZH.put("md", "摩尔多瓦");
        ZH.put("me", "黑山");
        ZH.put("mg", "马达加斯加");
        ZH.put("mk", "北马其顿");
        ZH.put("ml", "马里");
        ZH.put("mm", "缅甸");
        ZH.put("mn", "蒙古");
        ZH.put("mo", "澳门");
        ZH.put("mr", "毛里塔尼亚");
        ZH.put("ms", "蒙特塞拉特");
        ZH.put("mt", "马耳他");
        ZH.put("mu", "毛里求斯");
        ZH.put("mv", "马尔代夫");
        ZH.put("mw", "马拉维");
        ZH.put("mx", "墨西哥");
        ZH.put("my", "马来西亚");
        ZH.put("mz", "莫桑比克");
        ZH.put("na", "纳米比亚");
        ZH.put("nc", "新喀里多尼亚");
        ZH.put("ne", "尼日尔");
        ZH.put("ng", "尼日利亚");
        ZH.put("ni", "尼加拉瓜");
        ZH.put("nl", "荷兰");
        ZH.put("no", "挪威");
        ZH.put("np", "尼泊尔");
        ZH.put("nz", "新西兰");
        ZH.put("om", "阿曼");
        ZH.put("pa", "巴拿马");
        ZH.put("pe", "秘鲁");
        ZH.put("pg", "巴布亚新几内亚");
        ZH.put("ph", "菲律宾");
        ZH.put("pk", "巴基斯坦");
        ZH.put("pl", "波兰");
        ZH.put("pr", "波多黎各");
        ZH.put("ps", "巴勒斯坦");
        ZH.put("pt", "葡萄牙");
        ZH.put("pw", "帕劳");
        ZH.put("py", "巴拉圭");
        ZH.put("qa", "卡塔尔");
        ZH.put("re", "留尼汪");
        ZH.put("ro", "罗马尼亚");
        ZH.put("rs", "塞尔维亚");
        ZH.put("ru", "俄罗斯");
        ZH.put("rw", "卢旺达");
        ZH.put("sa", "沙特阿拉伯");
        ZH.put("sd", "苏丹");
        ZH.put("se", "瑞典");
        ZH.put("sg", "新加坡");
        ZH.put("si", "斯洛文尼亚");
        ZH.put("sk", "斯洛伐克");
        ZH.put("sl", "塞拉利昂");
        ZH.put("sm", "圣马力诺");
        ZH.put("sn", "塞内加尔");
        ZH.put("so", "索马里");
        ZH.put("sr", "苏里南");
        ZH.put("ss", "南苏丹");
        ZH.put("st", "圣多美和普林西比");
        ZH.put("sv", "萨尔瓦多");
        ZH.put("sy", "叙利亚");
        ZH.put("sz", "斯威士兰");
        ZH.put("td", "乍得");
        ZH.put("tg", "多哥");
        ZH.put("th", "泰国");
        ZH.put("tj", "塔吉克斯坦");
        ZH.put("tl", "东帝汶");
        ZH.put("tm", "土库曼斯坦");
        ZH.put("tn", "突尼斯");
        ZH.put("tr", "土耳其");
        ZH.put("tt", "特立尼达和多巴哥");
        ZH.put("tw", "台湾");
        ZH.put("tz", "坦桑尼亚");
        ZH.put("ua", "乌克兰");
        ZH.put("ug", "乌干达");
        ZH.put("us", "美国");
        ZH.put("uy", "乌拉圭");
        ZH.put("uz", "乌兹别克斯坦");
        ZH.put("va", "梵蒂冈");
        ZH.put("vc", "圣文森特和格林纳丁斯");
        ZH.put("ve", "委内瑞拉");
        ZH.put("vn", "越南");
        ZH.put("vu", "瓦努阿图");
        ZH.put("ws", "萨摩亚");
        ZH.put("ye", "也门");
        ZH.put("za", "南非");
        ZH.put("zm", "赞比亚");
        ZH.put("zw", "津巴布韦");
    }

    /**
     * Get the Chinese name for a country code (case-insensitive).
     * Returns null if not found.
     */
    public static String chineseName(String countryCode) {
        if (countryCode == null) return null;
        return ZH.get(countryCode.toLowerCase());
    }

    /**
     * Get a bilingual display name for a country code.
     * Format: "中文 EnglishName" (e.g. "新加坡 Singapore").
     * If no Chinese translation is available, returns just the English name.
     * If the English name is also null, returns the uppercased code.
     */
    public static String bilingual(String countryCode, String englishName) {
        String zh = chineseName(countryCode);
        if (zh == null) {
            return englishName != null && !englishName.isEmpty()
                    ? englishName
                    : (countryCode != null ? countryCode.toUpperCase() : "");
        }
        if (englishName == null || englishName.isEmpty()) {
            return zh;
        }
        return zh + " " + englishName;
    }

    /**
     * Same as {@link #bilingual(String, String)} but takes only the country code.
     */
    public static String bilingual(String countryCode) {
        return bilingual(countryCode, countryCode != null ? countryCode.toUpperCase() : "");
    }
}
