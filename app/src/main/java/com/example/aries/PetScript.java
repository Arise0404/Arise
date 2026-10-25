package com.example.aries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 AI 回复解析成桌宠动作序列。纯 Java（Java 8 语法，不依赖 android.*，可单独跑测试）。
 * 标记约定沿用桌宠上游写法：【词】= 先做这个表情/动作再打下一段；<词> = 打字打到这个位置时做。
 * 词表来自形象包 whale/figure.json 的 vocab（中文名与英文 id 都认），
 * 不认得的词只去掉括号、内容原样保留，绝不吞字，也不会误触发动作。
 */
public final class PetScript {

    public enum Kind { TEXT, WORD }

    public static final class Event {
        public final Kind kind;
        public final String value;
        public Event(Kind kind, String value) { this.kind = kind; this.value = value; }
        public boolean isWord() { return kind == Kind.WORD; }
        @Override public String toString() { return kind + "(" + value + ")"; }
        @Override public boolean equals(Object o) {
            return o instanceof Event && ((Event) o).kind == kind && ((Event) o).value.equals(value);
        }
        @Override public int hashCode() { return kind.hashCode() * 31 + value.hashCode(); }
    }

    private static final Map<String, String> NAMES = new HashMap<String, String>();
    private static final Map<String, String> KIND = new HashMap<String, String>();
    private static final List<String> WORDS = new ArrayList<String>();

    static {
        KIND.put("neutral", "expression");
        KIND.put("happy", "expression");
        KIND.put("wink", "expression");
        KIND.put("love", "expression");
        KIND.put("shy", "expression");
        KIND.put("surprised", "expression");
        KIND.put("angry", "expression");
        KIND.put("sad", "expression");
        KIND.put("sleepy", "expression");
        KIND.put("thinking", "expression");
        KIND.put("smug", "expression");
        KIND.put("pout", "expression");
        KIND.put("worried", "expression");
        KIND.put("determined", "expression");
        KIND.put("flustered", "expression");
        KIND.put("scared", "expression");
        KIND.put("excited", "expression");
        KIND.put("cry", "expression");
        KIND.put("confused", "expression");
        KIND.put("stand", "motion");
        KIND.put("jump", "motion");
        KIND.put("hop", "motion");
        KIND.put("look", "motion");
        KIND.put("turn", "motion");
        KIND.put("nod", "motion");
        KIND.put("shake", "motion");
        KIND.put("spin", "motion");
        KIND.put("sit", "motion");
        KIND.put("sleep", "motion");
        KIND.put("dizzy", "motion");
        KIND.put("walk", "motion");
        KIND.put("run", "motion");
        KIND.put("wave", "motion");
        KIND.put("bow", "motion");
        KIND.put("shiver", "motion");
        KIND.put("flap", "motion");
        KIND.put("dance", "motion");
        NAMES.put("angry", "angry");
        NAMES.put("bow", "bow");
        NAMES.put("confused", "confused");
        NAMES.put("cry", "cry");
        NAMES.put("dance", "dance");
        NAMES.put("determined", "determined");
        NAMES.put("dizzy", "dizzy");
        NAMES.put("excited", "excited");
        NAMES.put("flap", "flap");
        NAMES.put("flustered", "flustered");
        NAMES.put("happy", "happy");
        NAMES.put("hop", "hop");
        NAMES.put("jump", "jump");
        NAMES.put("look", "look");
        NAMES.put("love", "love");
        NAMES.put("neutral", "neutral");
        NAMES.put("nod", "nod");
        NAMES.put("pout", "pout");
        NAMES.put("run", "run");
        NAMES.put("sad", "sad");
        NAMES.put("scared", "scared");
        NAMES.put("shake", "shake");
        NAMES.put("shiver", "shiver");
        NAMES.put("shy", "shy");
        NAMES.put("sit", "sit");
        NAMES.put("sleep", "sleep");
        NAMES.put("sleepy", "sleepy");
        NAMES.put("smug", "smug");
        NAMES.put("spin", "spin");
        NAMES.put("stand", "stand");
        NAMES.put("surprised", "surprised");
        NAMES.put("thinking", "thinking");
        NAMES.put("turn", "turn");
        NAMES.put("walk", "walk");
        NAMES.put("wave", "wave");
        NAMES.put("wink", "wink");
        NAMES.put("worried", "worried");
        NAMES.put("伤心", "sad");
        NAMES.put("发抖", "shiver");
        NAMES.put("吃惊", "surprised");
        NAMES.put("吓到", "scared");
        NAMES.put("哆嗦", "shiver");
        NAMES.put("哭", "cry");
        NAMES.put("哼", "pout");
        NAMES.put("喜欢", "love");
        NAMES.put("嘚瑟", "smug");
        NAMES.put("嘟嘴", "pout");
        NAMES.put("困", "sleepy");
        NAMES.put("坐", "sit");
        NAMES.put("坐下", "sit");
        NAMES.put("坚定", "determined");
        NAMES.put("大哭", "cry");
        NAMES.put("害怕", "scared");
        NAMES.put("害羞", "shy");
        NAMES.put("小跳", "hop");
        NAMES.put("平静", "neutral");
        NAMES.put("开心", "happy");
        NAMES.put("张望", "look");
        NAMES.put("得意", "smug");
        NAMES.put("思考", "thinking");
        NAMES.put("惊讶", "surprised");
        NAMES.put("想想", "thinking");
        NAMES.put("慌张", "flustered");
        NAMES.put("扑腾", "flap");
        NAMES.put("打招呼", "wave");
        NAMES.put("担心", "worried");
        NAMES.put("招手", "wave");
        NAMES.put("摇头", "shake");
        NAMES.put("摇摆", "dance");
        NAMES.put("散步", "walk");
        NAMES.put("星星眼", "excited");
        NAMES.put("晕", "dizzy");
        NAMES.put("期待", "excited");
        NAMES.put("激动", "flap");
        NAMES.put("点头", "nod");
        NAMES.put("爱心", "love");
        NAMES.put("犯困", "sleepy");
        NAMES.put("生气", "angry");
        NAMES.put("疑惑", "confused");
        NAMES.put("看看", "look");
        NAMES.put("眨眼", "wink");
        NAMES.put("着急", "worried");
        NAMES.put("睡", "sleep");
        NAMES.put("睡觉", "sleep");
        NAMES.put("窘", "flustered");
        NAMES.put("站", "stand");
        NAMES.put("站起", "stand");
        NAMES.put("认真", "determined");
        NAMES.put("走走", "walk");
        NAMES.put("跑", "run");
        NAMES.put("跑起来", "run");
        NAMES.put("跳", "jump");
        NAMES.put("跳舞", "dance");
        NAMES.put("跳起来", "jump");
        NAMES.put("蹦", "hop");
        NAMES.put("转圈", "spin");
        NAMES.put("转晕", "dizzy");
        NAMES.put("转身", "turn");
        NAMES.put("问号", "confused");
        NAMES.put("难过", "sad");
        NAMES.put("鞠躬", "bow");
        NAMES.put("高兴", "happy");
        WORDS.addAll(java.util.Arrays.asList("neutral", "happy", "wink", "love", "shy", "surprised", "angry", "sad", "sleepy", "thinking", "smug", "pout", "worried", "determined", "flustered", "scared", "excited", "cry", "confused", "stand", "jump", "hop", "look", "turn", "nod", "shake", "spin", "sit", "sleep", "dizzy", "walk", "run", "wave", "bow", "shiver", "flap", "dance"));
    }

    private PetScript() { }

    public static String wordId(String name) {
        return name == null ? null : NAMES.get(name.trim());
    }

    public static String kind(String wordId) {
        return wordId == null ? null : KIND.get(wordId);
    }

    public static List<String> words() {
        return Collections.unmodifiableList(WORDS);
    }

    public static List<Event> parse(String raw) {
        List<Event> out = new ArrayList<Event>();
        if (raw == null || raw.length() == 0) return out;
        StringBuilder text = new StringBuilder();
        int i = 0, n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            char close = c == '【' ? '】' : (c == '<' ? '>' : 0);
            int end = close == 0 ? -1 : raw.indexOf(close, i + 1);
            // 不是标记、或没有闭合（比如 a<b 标签），都按普通字符走
            if (end < 0) { text.append(c); i++; continue; }
            String inner = raw.substring(i + 1, end).trim();
            String id = inner.length() == 0 ? null : wordId(inner);
            if (id != null) {
                flush(out, text);
                out.add(new Event(Kind.WORD, id));
                i = end + 1;
            } else {
                text.append(inner);      // 认不出就当正文，括号丢掉，内容一个字不少
                i = end + 1;
            }
        }
        flush(out, text);
        return out;
    }

    private static void flush(List<Event> out, StringBuilder text) {
        if (text.length() > 0) {
            out.add(new Event(Kind.TEXT, text.toString()));
            text.setLength(0);
        }
    }
}
