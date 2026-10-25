package com.example.aries;

/**
 * 模型没写【】标记时的兜底：按文本线索挑一个表情词。
 * 纯关键词启发式，不是情绪识别，挑不出来就返回 null（宁可不做表情，也别乱做）。
 * 规则顺序即优先级：先特异后泛化（比如 难过 要在 哭 之前，两个感叹号要在 哈哈 之前）。
 */
public final class PetMood {

    /** { 线索, 词 id }，自上而下第一个命中就用。id 必须来自形象包词表。 */
    private static final String[][] RULES = {
        // 强烈的：双感叹号算激动
        {"！！", "excited"}, {"!!", "excited"},
        // 难过系（放在哭之前，"我好难过想哭"该是 sad）
        {"难过", "sad"}, {"对不起", "sad"}, {"抱歉", "sad"}, {"难受", "sad"}, {"舍不得", "sad"}, {"心疼", "sad"},
        // 哭
        {"呜呜", "cry"}, {"大哭", "cry"}, {"泪", "cry"},
        // 亲近
        {"谢谢", "love"}, {"爱你", "love"}, {"喜欢你", "love"}, {"抱抱", "love"}, {"么么", "love"},
        // 困
        {"困", "sleepy"}, {"晚安", "sleepy"}, {"睡觉", "sleepy"}, {"好累", "sleepy"},
        // 疑惑
        {"不懂", "confused"}, {"不明白", "confused"}, {"什么意思", "confused"}, {"为什么", "confused"}, {"啊?", "confused"}, {"？?", "confused"},
        // 惊讶
        {"居然", "surprised"}, {"竟然", "surprised"}, {"没想到", "surprised"}, {"天哪", "surprised"}, {"不会吧", "surprised"},
        // 思考
        {"想想", "thinking"}, {"思考", "thinking"}, {"也许", "thinking"}, {"大概", "thinking"}, {"考虑", "thinking"}, {"让我看", "thinking"},
        // 担心
        {"注意安全", "worried"}, {"小心", "worried"}, {"记得", "worried"}, {"别忘了", "worried"}, {"担心", "worried"},
        // 下决心
        {"一定", "determined"}, {"必须", "determined"}, {"保证", "determined"}, {"搞定", "determined"}, {"交给我", "determined"},
        // 傲娇/嘟嘴
        {"哼哼", "smug"}, {"那当然", "smug"}, {"也不看看", "smug"},
        {"哼", "pout"}, {"不理你", "pout"}, {"人家", "pout"},
        // 害羞
        {"害羞", "shy"}, {"不好意思", "shy"}, {"羞", "shy"},
        // 生气
        {"气死", "angry"}, {"讨厌", "angry"}, {"烦死", "angry"},
        // 开心（放最后，兜住前面没接住的正面话）
        {"哈哈哈哈", "happy"}, {"哈哈", "happy"}, {"开心", "happy"}, {"太好了", "happy"}, {"太棒", "happy"},
        {"厉害", "happy"}, {"好耶", "happy"}, {"不错", "happy"}, {"完美", "happy"}, {"棒", "happy"},
    };

    private PetMood() { }

    /** 给这段话挑一个表情词 id；挑不出来返回 null。 */
    public static String pick(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.length() == 0) return null;
        for (String[] rule : RULES) {
            if (s.contains(rule[0])) return rule[1];
        }
        return null;
    }
}
