package com.example.aries;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class TopicAdapter extends BaseAdapter {

    public interface Callback {
        void onSelect(Topic topic);
        void onMore(Topic topic);
    }

    private final Context ctx;
    private final List<Topic> data;
    private final Callback callback;
    private final String currentId;

    public TopicAdapter(Context ctx, List<Topic> data, String currentId, Callback cb) {
        this.ctx = ctx;
        this.data = data;
        this.currentId = currentId;
        this.callback = cb;
    }

    @Override public int getCount() { return data.size(); }
    @Override public Object getItem(int i) { return data.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(ctx).inflate(R.layout.item_topic, parent, false);
        }
        final Topic topic = data.get(position);

        TextView tvTitle = convertView.findViewById(R.id.tvTopicTitle);
        TextView tvMore = convertView.findViewById(R.id.tvTopicMore);

        tvTitle.setText(topic.title);

        // 当前选中高亮
        if (topic.id.equals(currentId)) {
            tvTitle.setTextColor(ctx.getResources().getColor(R.color.bubble_user));
        } else {
            tvTitle.setTextColor(ctx.getResources().getColor(R.color.text_main));
        }

        // 主对话不显示"更多"按钮
        if ("main".equals(topic.type)) {
            tvMore.setVisibility(View.GONE);
        } else {
            tvMore.setVisibility(View.VISIBLE);
        }

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onSelect(topic);
            }
        });

        tvMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onMore(topic);
            }
        });

        return convertView;
    }
}