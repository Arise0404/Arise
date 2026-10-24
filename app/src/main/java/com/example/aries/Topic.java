package com.example.aries;

import org.json.JSONArray;
import org.json.JSONObject;

public class Topic {
    public String id;
    public String title;
    public String type;            // "main" 或 "branch"
    public JSONArray messages;     // [{role, content}, ...]
    public JSONArray enabledPromptIndexes;  // 本话题启用的提示词索引
    

    public Topic(String id, String title, String type) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.messages = new JSONArray();
        this.enabledPromptIndexes = new JSONArray();
        
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("type", type);
            o.put("messages", messages);
            o.put("enabledPromptIndexes", enabledPromptIndexes);
            // draft 不写入，退出即清
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static Topic fromJson(JSONObject o) {
        Topic t = new Topic(
                o.optString("id", ""),
                o.optString("title", ""),
                o.optString("type", "branch")
        );
        t.messages = o.optJSONArray("messages");
        if (t.messages == null) t.messages = new JSONArray();
        t.enabledPromptIndexes = o.optJSONArray("enabledPromptIndexes");
        if (t.enabledPromptIndexes == null) t.enabledPromptIndexes = new JSONArray();
        return t;
    }
}