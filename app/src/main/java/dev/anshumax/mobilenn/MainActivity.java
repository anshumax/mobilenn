package dev.anshumax.mobilenn;



import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.renderscript.Matrix4f;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.qualcomm.qti.snpe.NeuralNetwork;
import com.qualcomm.qti.snpe.SNPE;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private final String TAG = getClass().getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
//        {
//            requestPermissions(new String[]{android.Manifest.permission.CAMERA},0);
//        }else{
//            Log.i(TAG, "Camera permission already obtained");
//            Toast.makeText(this, "Camera Permission: " + (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.CAMERA) == PermissionChecker.PERMISSION_GRANTED),
//                    Toast.LENGTH_LONG).show();
//        }
//
//        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED)
//        {
//            requestPermissions(new String[]{android.Manifest.permission.INTERNET},0);
//        }else{
//            Log.i(TAG, "Internet permission already obtained");
//            Toast.makeText(this, "Internet Permission: " + (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.INTERNET) == PermissionChecker.PERMISSION_GRANTED),
//                    Toast.LENGTH_LONG).show();
//        }
        checkPermissionRequest(new String[]{Manifest.permission.CAMERA, Manifest.permission.INTERNET});
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Button nextButton = findViewById(R.id.nextButton);
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String camerId : manager.getCameraIdList()) {
                Log.i(TAG, "Camera ID " + camerId);
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
        nextButton.setOnClickListener((clickEvent) -> {
            Intent testCameraActivityIntent = new Intent(MainActivity.this.getApplicationContext(), TestCameraActivity.class);
            testCameraActivityIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            MainActivity.this.startActivity(testCameraActivityIntent);
        });

        this.getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                backConfirmation();
            }
        });


    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i = 0; i < permissions.length; i++) {
            Log.i(TAG, "Permission " + permissions[i] + " -> " + grantResults[i]);
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                TextView centralText = (TextView) findViewById(R.id.centralText);
                centralText.setText("Touched!");
        }
        return true;
    }


    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.

                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }

            });

    public void makePermissionRequest(String permission) {
        requestPermissionLauncher.launch(permission);
    }


    public void checkPermissionRequest(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {

            } else if (shouldShowRequestPermissionRationale(permission)) {
                showAlertDialog(permission);
            } else {
                makePermissionRequest(permission);
            }
        }
    }

    public void showAlertDialog(String permission) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage("This app needs you to allow this permission in order to function. Will you allow it?");
        alertDialogBuilder.setPositiveButton("Yes",
                (dialogue, which) -> makePermissionRequest(permission));

        alertDialogBuilder.setNegativeButton("No", (dialog, which) -> {

        });
    }

    private void backConfirmation() {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setCancelable(false);
        alertDialog.setTitle("Exit confirmation");
        alertDialog.setMessage("Are you sure you want to exit the application?");
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                (dialog, which) -> {
                    this.finishAffinity();
        });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }
}