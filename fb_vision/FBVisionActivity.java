package com.alpay.codenotes.activities;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.alpay.codenotes.R;
import com.alpay.codenotes.adapter.CodeBlockViewAdapter;
import com.alpay.codenotes.utils.NavigationManager;
import com.alpay.codenotes.utils.Utils;
import com.alpay.codenotes.vision.AutoMLImageLabelerProcessor;
import com.alpay.codenotes.vision.BarcodeScanningProcessor;
import com.alpay.codenotes.vision.CameraSource;
import com.alpay.codenotes.vision.CameraSourcePreview;
import com.alpay.codenotes.vision.GraphicOverlay;
import com.alpay.codenotes.vision.ObjectDetectorProcessor;
import com.alpay.codenotes.vision.TextRecognitionProcessor;
import com.alpay.codenotes.view.AutofitVerticalRecyclerView;
import com.alpay.codenotes.view.utils.MarginDecoration;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.alpay.codenotes.models.GroupHelper.codeList;
import static com.alpay.codenotes.utils.NavigationManager.BUNDLE_CODE_KEY;
import static com.alpay.codenotes.utils.NavigationManager.BUNDLE_FLAPPY_KEY;


public class FBVisionActivity extends BaseActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String OBJECT_DETECTION = "Object Detection";
    private static final String AUTOML_IMAGE_LABELING = "AutoML Vision Edge";
    private static final String TEXT_DETECTION = "Text Detection";
    private static final String BARCODE_DETECTION = "Barcode Detection";
    private static final String TAG = "LivePreviewActivity";
    private static final int PERMISSION_REQUESTS = 1;
    private boolean isFlappy = false;

    private com.alpay.codenotes.vision.CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private String selectedModel = TEXT_DETECTION;

    @BindView(R.id.read_code_button)
    FloatingActionButton readCodeButton;
    @BindView(R.id.codeblocks_recycler_view)
    AutofitVerticalRecyclerView blocksRecyclerView;

    CodeBlockViewAdapter codeBlockViewAdapter;

    @OnClick(R.id.read_code_button)
    public void readCode() {
        if (Utils.code != null) {
            codeList.add(Utils.code);
            refreshCodeBlockRecyclerView(codeList.size() - 1);
        } else {
            Utils.showOKDialog(this, R.string.no_code_dialog_message);
        }
    }

    @OnClick(R.id.send_code_button)
    public void sendCode() {
        String[] p5Code = codeList.toArray(new String[codeList.size()]);
        Intent intent = new Intent(this, CodeBlocksResultActivity.class);
        intent.putExtra(BUNDLE_CODE_KEY, p5Code);
        intent.putExtra(BUNDLE_FLAPPY_KEY, isFlappy);
        startActivity(intent);
    }

    @OnClick(R.id.read_barcode)
    public void readBarcode(){
        preview.stop();
        if (allPermissionsGranted()) {
            createCameraSource(BARCODE_DETECTION);
            startCameraSource();
        } else {
            getRuntimePermissions();
        }
    }

    @OnClick(R.id.back_code_button)
    public void backToHome() {
        super.onBackPressed();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fb_vision);
        ButterKnife.bind(this);
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            if (bundle.getStringArray(BUNDLE_CODE_KEY) != null) {
                String[] p5code = bundle.getStringArray(BUNDLE_CODE_KEY);
                for (String code : p5code) {
                    codeList.add(code);
                }
            }
            if (bundle.getString(NavigationManager.BUNDLE_KEY) != null) {
                openHintView(bundle.getString(NavigationManager.BUNDLE_KEY));
            }
            if (bundle.getBoolean(BUNDLE_FLAPPY_KEY)){
                isFlappy = true;
            }
        }

        preview = findViewById(R.id.firePreview);
        if (preview == null) {
            Log.d(TAG, "Preview is null");
        }
        graphicOverlay = findViewById(R.id.fireFaceOverlay);
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null");
        }

        if (allPermissionsGranted()) {
            createCameraSource(selectedModel);
        } else {
            getRuntimePermissions();
        }
        setUpRecyclerView();
        refreshCodeBlockRecyclerView(codeList.size() - 1);
    }

    private void openHintView(String instructions) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.task_title)
                .setMessage(instructions)
                .setNeutralButton(android.R.string.ok, (dialog, which) -> {
                    // Continue with operation
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void setUpRecyclerView() {
        blocksRecyclerView.addItemDecoration(new MarginDecoration(this));
        blocksRecyclerView.setHasFixedSize(true);
    }

    public void refreshCodeBlockRecyclerView(int position) {
        codeBlockViewAdapter = new CodeBlockViewAdapter(this, codeList);
        blocksRecyclerView.setAdapter(codeBlockViewAdapter);
        blocksRecyclerView.scrollToPosition(position);
    }


    private void createCameraSource(String model) {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
        }

        try {
            switch (model) {
                case TEXT_DETECTION:
                    Log.i(TAG, "Using Text Detector Processor");
                    cameraSource.setMachineLearningFrameProcessor(new TextRecognitionProcessor());
                    break;
                case AUTOML_IMAGE_LABELING:
                    cameraSource.setMachineLearningFrameProcessor(new AutoMLImageLabelerProcessor(this));
                    break;
                case OBJECT_DETECTION:
                    Log.i(TAG, "Using Object Detector Processor");
                    FirebaseVisionObjectDetectorOptions objectDetectorOptions =
                            new FirebaseVisionObjectDetectorOptions.Builder()
                                    .setDetectorMode(FirebaseVisionObjectDetectorOptions.STREAM_MODE)
                                    .enableClassification().build();
                    cameraSource.setMachineLearningFrameProcessor(
                            new ObjectDetectorProcessor(objectDetectorOptions));
                    break;
                case BARCODE_DETECTION:
                    Log.i(TAG, "Using Barcode Detector Processor");
                    cameraSource.setMachineLearningFrameProcessor(new BarcodeScanningProcessor());
                    break;
                default:
                    Log.e(TAG, "Unknown model: " + model);
            }
        } catch (Exception e) {
            Log.e(TAG, "Can not create image processor: " + model, e);
            Toast.makeText(
                    getApplicationContext(),
                    "Can not create image processor: " + e.getMessage(),
                    Toast.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                    this.getPackageManager()
                            .getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                return false;
            }
        }
        return true;
    }

    private void getRuntimePermissions() {
        List<String> allNeededPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (!isPermissionGranted(this, permission)) {
                allNeededPermissions.add(permission);
            }
        }

        if (!allNeededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toArray(new String[0]), PERMISSION_REQUESTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "Permission granted!");
        if (allPermissionsGranted()) {
            createCameraSource(selectedModel);
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private static boolean isPermissionGranted(Context context, String permission) {
        if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted: " + permission);
            return true;
        }
        Log.i(TAG, "Permission NOT granted: " + permission);
        return false;
    }

}