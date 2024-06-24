package dev.anshumax.mobilenn;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;

import com.google.android.material.snackbar.Snackbar;
import com.qualcomm.qti.snpe.FloatTensor;
import com.qualcomm.qti.snpe.NeuralNetwork;
import com.qualcomm.qti.snpe.SNPE;

import org.junit.rules.Stopwatch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestCameraActivity extends AppCompatActivity {
    private final String TAG = TestCameraActivity.class.getName();
    final String MNETSSD_OUTPUT_LAYER = "ArgMax:0";
    final String MNETSSD_INPUT_LAYER = "sub_7:0";
    final int BITMAP_WIDTH = 513;
    final int BITMAP_HEIGHT = 513;
    final int BITMAP_DEPTH = 3;
    int MNETSSD_NUM_BOXES = BITMAP_WIDTH * BITMAP_HEIGHT;

    Bitmap image, outputBitmap;
    // Define the button and imageview type variable
    Button cameraButton, processButton;
    ImageView clickImageView;
    Uri imageUri;
    NeuralNetwork neuralNetwork;
    BitmapToFloatArrayHelper bitmapToFloatArrayHelper;
    ProgressBar spinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_camera_activity);

        cameraButton = findViewById(R.id.camera_button);
        processButton = findViewById(R.id.process_button);
        clickImageView = findViewById(R.id.click_image);

        this.getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                backPressed();
            }
        });

        cameraButton.setOnClickListener((l) -> {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, "New Picture");
            values.put(MediaStore.Images.Media.DESCRIPTION, "From your Camera");
            imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            Intent camera_intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camera_intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);

            cameraActivityLauncher.launch(camera_intent);
        });

        processButton.setOnClickListener((l) -> {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(() -> {
                Log.i(TAG, "image object is null: " + Objects.isNull(image));
                if(Objects.isNull(image)){
                    runOnUiThread(() -> {
                        Snackbar.make(findViewById(R.id.camera_button), "No image available", Snackbar.LENGTH_LONG).show();
                    });
                } else {
                    Log.i(TAG, "running inference");
                    runOnUiThread(() -> {
                        spinner.setVisibility(View.VISIBLE);
                    });
                    try{
                        long startTime = System.currentTimeMillis();
                        inferenceOnBitmap(image);
                        long endTime = System.currentTimeMillis();
                        Log.i(TAG, " Time taken in ms: " + (endTime - startTime));
                    }catch (Exception e){
                        runOnUiThread(() -> {
                            Toast.makeText(TestCameraActivity.this.getApplicationContext(), "Error " + e.getMessage(), Toast.LENGTH_LONG).show();
                            spinner.setVisibility(View.GONE);
                        });
                    }
                }

                handler.post(() -> {
                    clickImageView.setImageBitmap(outputBitmap);
                    runOnUiThread(() -> spinner.setVisibility(View.GONE));
                });
            });
        });


    }

    ActivityResultLauncher<Intent> cameraActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        try {
                            image = MediaStore.Images.Media.getBitmap(
                                    getContentResolver(), imageUri);
                            clickImageView.setImageBitmap(image);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                    }
                }
            });

    public String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
    @Override
    protected void onStart() {
        super.onStart();
        if(Objects.isNull(neuralNetwork)){
            spinner = findViewById(R.id.pb);
            spinner.setVisibility(View.VISIBLE);
            Thread thread = new Thread(() -> {
                try {
                    Log.i(TAG, "Creating new cached model file");

                    File cachedModelFile = new File(getApplicationContext().getCacheDir(), "quantized_deeplab.dlc");
                    Files.deleteIfExists(cachedModelFile.toPath());


                    for (String path : this.getAssets().list("")) {
                        Log.i(TAG, "Path -> " + path);
                    }

                    Log.i(TAG, "Copying data to new cached model");
                    int i = 0;
                    InputStream inputStream = getAssets().open("deeplabv3.dlc");
                    int size = inputStream.available();
                    byte[] buffer = new byte[size];
                    inputStream.read(buffer);
                    Files.write(cachedModelFile.toPath(), buffer);

                    Log.i(TAG, "Cached file has size " + Files.size(cachedModelFile.toPath()) + " bytes");
                    Log.i(TAG, "Building model from cached file");
                    SNPE.NeuralNetworkBuilder builder = new
                            SNPE.NeuralNetworkBuilder(getApplication())
                            .setRuntimeOrder(NeuralNetwork.Runtime.DSP, NeuralNetwork.Runtime.GPU, NeuralNetwork.Runtime.CPU)
                            .setModel(cachedModelFile);

                    neuralNetwork = builder.build();

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                Log.i(TAG, "Model built");
                runOnUiThread(() -> {
                    spinner.setVisibility(View.GONE);
                    Toast.makeText(TestCameraActivity.this.getApplicationContext(), "Model built", Toast.LENGTH_LONG).show();
                });
            });
            thread.start();
        }
    }

    private void inferenceOnBitmap(Bitmap bitmap) {
        BitmapToFloatArrayHelper bitmapToFloatArrayHelper = new BitmapToFloatArrayHelper();
        int originalBitmapH = bitmap.getHeight();
        int originalBitmapW = bitmap.getWidth();
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, BITMAP_WIDTH, BITMAP_HEIGHT, false);
        final float[] floatOutput = new float[MNETSSD_NUM_BOXES];
        try {
            int[] mInputTensorShapeHWC = neuralNetwork.getInputTensorsShapes().get(MNETSSD_INPUT_LAYER);
            Map<String, int[]> tensorShapes = neuralNetwork.getInputTensorsShapes();
            for(Map.Entry<String, int[]> entry: tensorShapes.entrySet()){
                Log.i(TAG, entry.getKey() + " -> " + entry.getValue().length);
                for(int i: entry.getValue()){
                    Log.i(TAG, "value -> " + i);
                }
            }

            // allocate the single input tensor
            FloatTensor mInputTensorReused = neuralNetwork.createFloatTensor(mInputTensorShapeHWC);
            // add it to the map of inputs, even if it's a single input
            Map<String, FloatTensor> mInputTensorsMap = new HashMap<>();
            mInputTensorsMap.put(MNETSSD_INPUT_LAYER, mInputTensorReused);

            if (neuralNetwork == null){
                Log.e(TAG, "No NN loaded");
            } else if (mInputTensorReused == null){
                Log.e(TAG, "No Input tensor");
            }  else if (scaledBitmap.getWidth() != mInputTensorShapeHWC[1]){
                Log.e(TAG, "Scaled bitmap width is different from input tensor");
            } else if (scaledBitmap.getHeight() != mInputTensorShapeHWC[2]) {
                Log.e(TAG, "Scaled bitmap height is different from input tensor");
            }
            //Bitmap to RGBA byte array (size: 513*513*3 (RGBA..))
            bitmapToFloatArrayHelper.bitmapToBuffer(scaledBitmap);
            //Pre-processing: Bitmap (513,513,4 ints) -> Float Input Tensor (513,513,3 floats)
            final float[] inputFloatsHW3 = bitmapToFloatArrayHelper.bufferToNormalFloatsBGR();
            if (bitmapToFloatArrayHelper.isFloatBufferBlack())
                throw new Exception("Black Float Buffer");
            mInputTensorReused.write(inputFloatsHW3, 0, inputFloatsHW3.length, 0, 0);
            // execute the inference
            Map<String, FloatTensor> outputs = neuralNetwork.execute(mInputTensorsMap);

            if (outputs != null) {
                MNETSSD_NUM_BOXES = outputs.get(MNETSSD_OUTPUT_LAYER).getSize();
                // convert tensors to boxes - Note: Optimized to read-all upfront
                outputs.get(MNETSSD_OUTPUT_LAYER).read(floatOutput, 0, MNETSSD_NUM_BOXES);
                //for black/white image
                int w = scaledBitmap.getWidth();
                int h = scaledBitmap.getHeight();
                int b = 0xFF;
                int out = 0xFF;

                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        b = b & scaledBitmap.getPixel(x, y);
                        for (int i = 1; i <= 3 && floatOutput[y * w + x] != 15; i++) {
                            out = out << (8) | b;
                        }
                        scaledBitmap.setPixel(x, y, floatOutput[y * w + x] != 15 ? out : scaledBitmap.getPixel(x, y));
                        out = 0xFF;
                        b = 0xFF;
                    }
                }

                outputBitmap = Bitmap.createScaledBitmap(scaledBitmap, originalBitmapW,
                        originalBitmapH, false);

            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getCause() + "");
        }
    }

    private void backConfirmation() {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setCancelable(false);
        alertDialog.setTitle("Go Back?");
        alertDialog.setMessage("Are you sure you want to go back?");
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                (dialog, which) -> {
//                    TestCameraActivity.this.finish();
                    Intent mainActivityIntent = new Intent(TestCameraActivity.this.getApplicationContext(), MainActivity.class);
                    mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    TestCameraActivity.this.startActivity(mainActivityIntent);

                    overridePendingTransition(android.R.anim.slide_out_right, android.R.anim.slide_in_left);
                });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }


    public void backPressed()
    {
        Bundle bundle =
                ActivityOptions.makeCustomAnimation(getApplicationContext(),
                        R.anim.slide_from_left, R.anim.slide_to_right).toBundle();
        Intent mainActivityIntent = new Intent(TestCameraActivity.this.getApplicationContext(), MainActivity.class);
        mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        TestCameraActivity.this.startActivity(mainActivityIntent, bundle);
    }
}
