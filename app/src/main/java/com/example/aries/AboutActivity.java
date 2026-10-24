package com.example.aries;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        TextView tvVersion = findViewById(R.id.tvVersion);
        TextView tvModel = findViewById(R.id.tvModel);
        TextView tvApi = findViewById(R.id.tvApi);

        tvVersion.setText("v1.0");

        String modelName = "Qwen2.5-3B / Qwen2.5-7B-abliterated";
        tvModel.setText(modelName);

        tvApi.setText(Prefs.getApiUrl(this));
    }
}