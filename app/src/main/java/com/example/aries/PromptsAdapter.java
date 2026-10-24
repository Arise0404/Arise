package com.example.aries;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class PromptsAdapter extends BaseAdapter {

    public interface Callback {
        void onEdit(int position);
        void onDelete(int position);
    }

    private final Context ctx;
    private final List<PromptsActivity.Prompt> data;
    private final Callback callback;

    public PromptsAdapter(Context ctx, List<PromptsActivity.Prompt> data, Callback cb) {
        this.ctx = ctx;
        this.data = data;
        this.callback = cb;
    }

    @Override public int getCount() { return data.size(); }
    @Override public Object getItem(int i) { return data.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(ctx).inflate(R.layout.item_prompt, parent, false);
        }
        final PromptsActivity.Prompt p = data.get(position);

        TextView tvPrompt = convertView.findViewById(R.id.tvPrompt);
        tvPrompt.setText(p.title);

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { callback.onEdit(position); }
        });

        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                callback.onDelete(position);
                return true;
            }
        });

        return convertView;
    }
}