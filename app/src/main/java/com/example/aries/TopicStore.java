package com.example.aries;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class TopicStore {

    private static final String KEY = "topics_data";
    private static final String CURRENT = "current_topic_id";

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences("aries_settings", Context.MODE_PRIVATE);
    }

    // 读取全部话题
    public static List<Topic> getAll(Context ctx) {
        List<Topic> result = new ArrayList<>();
        try {
            String s = sp(ctx).getString(KEY, "");
            if (!s.isEmpty()) {
                JSONArray arr = new JSONArray(s);
                for (int i = 0; i < arr.length(); i++) {
                    result.add(Topic.fromJson(arr.getJSONObject(i)));
                }
            }
        } catch (Exception e) { /* 忽略 */ }

        if (result.isEmpty()) {
            Topic main = new Topic("main", Prefs.getAiName(ctx), "main");
            result.add(main);
            saveAll(ctx, result);
            setCurrentId(ctx, "main");
        }
        return result;
    }

    // 保存全部话题
    public static void saveAll(Context ctx, List<Topic> topics) {
        try {
            JSONArray arr = new JSONArray();
            for (Topic t : topics) arr.put(t.toJson());
            sp(ctx).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception e) { /* 忽略 */ }
    }

    // 主对话
    public static Topic getMain(Context ctx) {
        for (Topic t : getAll(ctx)) {
            if ("main".equals(t.type)) return t;
        }
        return null;
    }

    // 按 id 查
    public static Topic findById(Context ctx, String id) {
        for (Topic t : getAll(ctx)) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    // 新建支线
    public static Topic createBranch(Context ctx) {
        List<Topic> all = getAll(ctx);
        String id = "t_" + System.currentTimeMillis();
        Topic t = new Topic(id, "新对话", "branch");
        all.add(t);
        saveAll(ctx, all);
        return t;
    }

    // 删除支线（主对话不能删）
    public static void deleteBranch(Context ctx, String id) {
        List<Topic> all = getAll(ctx);
        List<Topic> result = new ArrayList<>();
        for (Topic t : all) {
            if (t.id.equals(id) && !"main".equals(t.type)) continue;
            result.add(t);
        }
        saveAll(ctx, result);
        if (id.equals(getCurrentId(ctx))) setCurrentId(ctx, "main");
    }

    // 当前话题 id
    public static String getCurrentId(Context ctx) {
        return sp(ctx).getString(CURRENT, "main");
    }

    public static void setCurrentId(Context ctx, String id) {
        sp(ctx).edit().putString(CURRENT, id).apply();
    }

    // 更新单个话题
    public static void update(Context ctx, Topic topic) {
        List<Topic> all = getAll(ctx);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(topic.id)) {
                all.set(i, topic);
                saveAll(ctx, all);
                return;
            }
        }
    }

    // 获取当前话题
    public static Topic getCurrent(Context ctx) {
        String id = getCurrentId(ctx);
        Topic t = findById(ctx, id);
        if (t == null) t = getMain(ctx);
        return t;
    }
}