package com.zzh.herbrecognition;

import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.zzh.herbrecognition.databinding.ActivityDetailBinding;

import com.alibaba.dashscope.utils.Constants;

import java.util.Arrays;

public class DetailActivity extends AppCompatActivity {
    ActivityDetailBinding binding;
    public static final int MESSAGE_REQUEST_SUCCESS = 100;
    public static final int MESSAGE_REQUEST_FAIL = 101;

    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Bundle bundle = getIntent().getExtras();
        String label = bundle.getString("label");
        float confidence = bundle.getFloat("confidence");

        handler = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(@NonNull android.os.Message msg) {
                if (msg.what == MESSAGE_REQUEST_SUCCESS) {
                    Bundle bundle = msg.getData();
                    String detail = bundle.getString("detail");
                    binding.tvDetail.setText(detail);
                } else if (msg.what == MESSAGE_REQUEST_FAIL) {
                    Toast.makeText(DetailActivity.this, "请求失败", Toast.LENGTH_SHORT).show();
                }
            }
        };

        binding.tvTitle.setText(label);
        binding.tvConfidence.setText("置信度：" + confidence);

        Constants.apiKey = "sk-da56e85422f84f8dada43df55fd1e238";
        fetchDetail(label);
    }

    private void fetchDetail(String herbName) {
        new Thread(() -> {
            try {
                GenerationResult result = callWithMessage(herbName);
                Bundle bundle = new Bundle();

                String detail = result.getOutput().getChoices().get(0).getMessage().getContent();

                bundle.putString("detail", detail);
                android.os.Message msg = new android.os.Message();
                msg.what = MESSAGE_REQUEST_SUCCESS;
                msg.setData(bundle);
                handler.sendMessage(msg);
            } catch (NoApiKeyException | InputRequiredException e) {
                android.os.Message msg = new android.os.Message();
                msg.what = MESSAGE_REQUEST_FAIL;
                handler.sendMessage(msg);
            }
        }).start();
    }

    public static GenerationResult callWithMessage(String herbName) throws ApiException, NoApiKeyException, InputRequiredException {
        Generation gen = new Generation();

        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content("你是一个中草药专家，请你简要介绍我给你提供的中药名称对应的中药")
                .build();

        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(herbName)
                .build();

        GenerationParam param = GenerationParam.builder()
                .model("qwen-turbo")
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .topP(0.8)
                .build();

        return gen.call(param);
    }
}