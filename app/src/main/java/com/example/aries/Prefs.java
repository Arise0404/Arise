package com.example.aries;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String FILE_NAME = "aries_settings";

    private static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    // ============ 系统提示词 ============

    public static String getPrompts(Context ctx) {
        return get(ctx).getString("prompts", "");
    }

    public static void setPrompts(Context ctx, String value) {
        get(ctx).edit().putString("prompts", value).apply();
    }

    public static int getActivePromptIndex(Context ctx) {
        return get(ctx).getInt("active_prompt_index", -1);
    }

    public static void setActivePromptIndex(Context ctx, int index) {
        get(ctx).edit().putInt("active_prompt_index", index).apply();
    }

    // ============ 外观 ============
    // 0 = 跟随系统, 1 = 深色, 2 = 浅色

    public static int getThemeMode(Context ctx) {
        return get(ctx).getInt("theme_mode", 0);
    }

    public static void setThemeMode(Context ctx, int mode) {
        get(ctx).edit().putInt("theme_mode", mode).apply();
    }

    // ============ 字体大小 ============

    public static int getFontSize(Context ctx) {
        return get(ctx).getInt("font_size", 15);
    }

    public static void setFontSize(Context ctx, int size) {
        get(ctx).edit().putInt("font_size", size).apply();
    }

    // ============ API 地址 ============

    public static String getApiUrl(Context ctx) {
        return get(ctx).getString("api_url", "http://127.0.0.1:8080/v1/chat/completions");
    }

    public static void setApiUrl(Context ctx, String url) {
        get(ctx).edit().putString("api_url", url).apply();
    }

    // ============ 模型参数 ============

    public static float getTemperature(Context ctx) {
        return get(ctx).getFloat("temperature", 0.7f);
    }

    public static void setTemperature(Context ctx, float v) {
        get(ctx).edit().putFloat("temperature", v).apply();
    }

    public static int getMaxTokens(Context ctx) {
        return get(ctx).getInt("max_tokens", 512);
    }

    public static void setMaxTokens(Context ctx, int v) {
        get(ctx).edit().putInt("max_tokens", v).apply();
    }

    // ============ AI 名称 ============

    public static String getAiName(Context ctx) {
        return get(ctx).getString("ai_name", "Aries");
    }

    public static void setAiName(Context ctx, String name) {
        get(ctx).edit().putString("ai_name", name).apply();
    }
    // ============ 系统提示词（列表版） ============
    // 存成 JSON 数组字符串

    public static String getPromptListJson(Context ctx) {
        return get(ctx).getString("prompt_list", "[]");
    }

    public static void setPromptListJson(Context ctx, String json) {
        get(ctx).edit().putString("prompt_list", json).apply();
    }
    
    public static int getApiSource(Context ctx) {
        return get(ctx).getInt("api_source", 0);
    }

    public static String getGeminiKey(Context ctx) {
        return get(ctx).getString("gemini_key", "");
    }
    
    public static void setApiSource(Context ctx, int source) {
        get(ctx).edit().putInt("api_source", source).apply();
    }

    public static void setGeminiKey(Context ctx, String key) {
        get(ctx).edit().putString("gemini_key", key).apply();
    }
    
    // ============ 用户设定 ============

    public static String getUserName(Context ctx) {
        return get(ctx).getString("user_name", "用户");
    }

    public static void setUserName(Context ctx, String name) {
        get(ctx).edit().putString("user_name", name).apply();
    }

    public static String getUserRole(Context ctx) {
        return get(ctx).getString("user_role", "主人");
    }

    public static void setUserRole(Context ctx, String role) {
        get(ctx).edit().putString("user_role", role).apply();
    }
    
    public static boolean isCompressEnabled(Context ctx) {
        return get(ctx).getBoolean("compress_image", true); // 默认开启
    }
    public static void setCompressEnabled(Context ctx, boolean enabled) {
        get(ctx).edit().putBoolean("compress_image", enabled).apply();
    }
    
    // ============ 模型高级参数 ============
    public static float getTopP(Context ctx) {
        return get(ctx).getFloat("param_top_p", 0.95f);
    }
    public static void setTopP(Context ctx, float v) {
        get(ctx).edit().putFloat("param_top_p", v).apply();
    }

    public static float getRepeatPenalty(Context ctx) {
        return get(ctx).getFloat("param_penalty", 1.1f);
    }
    public static void setRepeatPenalty(Context ctx, float v) {
        get(ctx).edit().putFloat("param_penalty", v).apply();
    }
    
    // ============ 绘图服务源 ============
    public static int getDrawSource(Context ctx) {
        return get(ctx).getInt("draw_source", 0); // 默认云端
    }

    public static void setDrawSource(Context ctx, int source) {
        get(ctx).edit().putInt("draw_source", source).apply();
    }
    
    // 桌宠模式：0=静态呼吸，1=序列帧，2=Live2D/WebView
    public static int getPetMode(Context ctx) {
        return get(ctx).getInt("pet_mode", 0); 
    }
    
    public static void setPetMode(Context ctx, int mode) {
        get(ctx).edit().putInt("pet_mode", mode).apply();
    }
    
    // ============ 桌宠开关 ============
    public static boolean isPetEnabled(Context ctx) {
        return get(ctx).getBoolean("pet_enabled", false);
    }
    public static void setPetEnabled(Context ctx, boolean enabled) {
        get(ctx).edit().putBoolean("pet_enabled", enabled).apply();
    }
}