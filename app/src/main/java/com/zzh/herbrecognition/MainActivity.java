package com.zzh.herbrecognition;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.databinding.DataBindingUtil;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zzh.herbrecognition.databinding.ActivityMainBinding;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "zzh main activity";
    public static final int REQUEST_PERMISSIONS = 100;
    public static final int REQUEST_IMAGE_CAPTURE = 10;
    public static final int REQUEST_PICK_IMAGE = 11;
    private final String[] permissions = {
            Manifest.permission.CAMERA,
    };
    public static final String INFERENCE_API_PATH = "/api/predict";
    public static final int MESSAGE_REQUEST_SUCCESS = 201;
    public static final int MESSAGE_REQUEST_FAIL = 202;

    ActivityMainBinding binding;
    Uri imageUri;
    String currentPhotoPath;

    OkHttpClient okHttpClient;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        handler = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == MESSAGE_REQUEST_SUCCESS) {
                    Bundle bundle = msg.getData();
                    String label = bundle.getString("label");
                    float confidence = bundle.getFloat("confidence");
                    Toast.makeText(MainActivity.this, "识别成功", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(MainActivity.this, DetailActivity.class);
                    intent.putExtras(bundle);
                    startActivity(intent);
                } else if (msg.what == MESSAGE_REQUEST_FAIL) {
                    Toast.makeText(MainActivity.this, "网络请求失败", Toast.LENGTH_SHORT).show();
                }
            }
        };

        if (!hasPermissions()) {
            requestPermissions();
        }

        binding.cameraButton.setOnClickListener(v -> {
            if (hasPermissions()) {
                captureImage();
            } else {
                requestPermissions();
            }
        });
        binding.galleryButton.setOnClickListener(v -> {
            if(hasPermissions()) {
                pickImage();
            } else {
                requestPermissions();
            }
        });
        binding.btnRecognize.setOnClickListener(v -> {
            if (imageUri != null && !binding.etIp.getText().toString().isEmpty()) {
                try {
                    Bitmap bitmap = getBitmapFromUri(imageUri);
                    sendImageToServer(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(this, "请选择图片并填写推理机IP", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendImageToServer(Bitmap bitmap) {
        new Thread(() -> {
            okHttpClient = new OkHttpClient();

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
            String encodedImage = Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());

            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("image", encodedImage);

            String address = binding.etIp.getText().toString();
            RequestBody body = RequestBody.create(jsonObject.toString(), MediaType.parse("application/json; charset=utf-8"));
            String url = "http://" + address + INFERENCE_API_PATH;
            Log.i(TAG, "URL: "+url);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            okHttpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    e.printStackTrace();
                    Message message = handler.obtainMessage(MESSAGE_REQUEST_FAIL);
                    handler.sendMessage(message);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseData = response.body().string();
                        Gson gson = new Gson();
                        JsonObject jsonResponse = gson.fromJson(responseData, JsonObject.class);
                        String label = jsonResponse.get("label").getAsString();
                        float confidence = jsonResponse.get("confidence").getAsFloat();

                        Message msg = handler.obtainMessage(MESSAGE_REQUEST_SUCCESS);
                        Bundle bundle = new Bundle();
                        bundle.putString("label", label);
                        bundle.putFloat("confidence", confidence);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                    }
                }
            });
        }).start();
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        return BitmapFactory.decodeStream(inputStream);
    }

    private boolean hasPermissions() {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
    }

    private void pickImage() {
        Intent pickPhotoIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickPhotoIntent, REQUEST_PICK_IMAGE);
    }

    private void captureImage() {
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (captureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.zzh.herbrecognition.fileprovider",
                        photoFile);
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(captureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0) {
                boolean allGranted = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
                if (!allGranted) {
                    // 权限被拒绝，显示一条消息或采取适当的措施
                    Toast.makeText(this, "获取权限失败！", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            File file = new File(currentPhotoPath);
            imageUri = Uri.fromFile(file);
            binding.img.setImageURI(imageUri);
        } else if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
            binding.img.setImageURI(imageUri);
        }
    }
}