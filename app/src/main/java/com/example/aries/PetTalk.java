package com.example.aries;

/**
 * 主人戳它时要不要主动开口、以及开口时对模型说什么。
 * 纯函数部分（节流判定 + 文案组装）可单测，安卓侧只负责落库和起服务。
 */
public final class PetTalk {

    /** 45 秒内连戳只许开口一次，免得刷屏，也免得白烧 token。 */
    public static final long COOLDOWN_MS = 45_000L;

    private PetTalk() { }

    /**
     * lastMs 为 0 表示从没主动说过，直接放行；否则要距上次满冷却才放行。
     * 边界：刚好等于 cooldown 算到点。
     */
    public static boolean allow(long nowMs, long lastMs, long cooldownMs) {
        if (lastMs <= 0L) return true;
        return nowMs - lastMs >= cooldownMs;
    }

    /** 造一条以主人视角发生的"事件"给模型，让它用桌宠口吻接一句。 */
    public static String pokePrompt(String petName, String touchKind) {
        String who = (petName == null || petName.trim().length() == 0) ? "你" : petName.trim();
        String kind = touchKind == null ? "" : touchKind;
        String scene;
        if ("grab".equals(kind)) scene = "主人把" + who + "拎起来了，两只脚在空中乱蹬";
        else if ("pet".equals(kind)) scene = "主人摸了摸" + who + "的头";
        else if ("throw".equals(kind)) scene = "主人把" + who + "甩了出去";
        else if ("drop".equals(kind)) scene = "主人把" + who + "放回桌面";
        else if ("crash".equals(kind)) scene = "主人把" + who + "甩出去摔疼了";
        else if ("poke".equals(kind)) scene = "主人戳了戳" + who;
        else scene = "主人跟" + who + "互动了一下";
        return "（桌面场景）" + scene + "。用" + who + "自己的口吻接一句，20 字以内，"
                + "先写一个表情标记再说话，比如【开心】" + "哎?" + "——只回这一句，不要解释。";
    }
}
