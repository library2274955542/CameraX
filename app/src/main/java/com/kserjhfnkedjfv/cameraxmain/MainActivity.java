package com.kserjhfnkedjfv.cameraxmain;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.kserjhfnkedjfv.camerax.util.CameraConstant;
import com.kserjhfnkedjfv.camerax.util.CameraParam;

import java.util.Iterator;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static String TAG = "MainActivity33333";
    ActivityResultLauncher<String> p = registerForActivityResult(new ActivityResultContracts.RequestPermission(), new ActivityResultCallback<Boolean>() {
        @Override
        public void onActivityResult(Boolean result) {

        }
    });
    String[] mPermission = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA};
    private TextView tv_camera;
    private ImageView img_picture;
    ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
        @Override
        public void onActivityResult(ActivityResult result) {
            if (result.getResultCode() == RESULT_OK) {
                String picturePath = result.getData().getStringExtra(CameraConstant.PICTURE_PATH_KEY);
                //显示出来
                img_picture.setVisibility(View.VISIBLE);
                img_picture.setImageBitmap(BitmapFactory.decodeFile(picturePath));
            } else {
                Toast.makeText(MainActivity.this, "" + result.getResultCode(), Toast.LENGTH_LONG).show();
            }
        }
    });
    ActivityResultLauncher<String[]> ps = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
        @Override
        public void onActivityResult(Map<String, Boolean> result) {
            Iterator<String> iterator = result.keySet().iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                boolean value = result.get(key);
                if (!value) {
                    Toast.makeText(MainActivity.this, "eeee", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            new CameraParam.Builder(MainActivity.this)
                    //.setMaskDimensionRatio("h,8:5")
                    .setShowFocusTips(false)
                    .build(launcher);
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);


        tv_camera = findViewById(R.id.tv_camera);
        img_picture = findViewById(R.id.img_picture);

        //!!!必选要有权限拍照和文件存储权限
        tv_camera.setOnClickListener(v -> ps.launch(mPermission));
    }


}