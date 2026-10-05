package com.pokemoncardmeter.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION = 1001;
    private static final String SERVER_URL = "https://pokemon-card-meter-api.onrender.com";

    private TextureView textureView;
    private GuideOverlay guideOverlay;
    private ImageView capturedView;
    private TextView statusText;
    private TextView helperText;
    private TextView aiRawText;
    private Button captureButton;
    private Button analyzeButton;
    private EditText cardNameInput;
    private EditText setCodeInput;
    private EditText cardNumberInput;
    private EditText hpInput;
    private EditText rarityInput;
    private EditText languageInput;
    private TextView confidenceText;
    private TextView dbStatusText;
    private TextView dbNameText;
    private TextView dbSetText;
    private TextView dbNumberText;
    private TextView dbCategoryText;
    private TextView dbRarityText;
    private Button marketButton;
    private TextView marketStatusText;
    private TextView minPriceText;
    private TextView averagePriceText;
    private TextView maxPriceText;
    private TextView marketSampleText;
    private TextView marketListingsText;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewBuilder;
    private ImageReader imageReader;
    private Size previewSize;
    private String cameraId;
    private int sensorOrientation = 90;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private File lastPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(8, 17, 47));
        getWindow().setNavigationBarColor(Color.rgb(8, 17, 47));
        buildUi();
        textureView.setSurfaceTextureListener(surfaceListener);
        captureButton.setOnClickListener(v -> {
            if (capturedView.getVisibility() == View.VISIBLE) {
                showCameraAgain();
            } else {
                takePicture();
            }
        });
        analyzeButton.setOnClickListener(v -> analyzeCapturedCard());
        marketButton.setOnClickListener(v -> searchMarket());
        
    }

    private void buildUi() {
        int navy = Color.rgb(8, 17, 47);
        int panel = Color.rgb(20, 34, 77);
        int yellow = Color.rgb(255, 216, 70);
        int white = Color.WHITE;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(navy);
        setContentView(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(22));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView badge = label("포켓몬 카드 시세 확인", 12, Color.rgb(23, 33, 63), true);
        badge.setBackground(roundRect(yellow, dp(18)));
        badge.setPadding(dp(12), dp(7), dp(12), dp(7));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(badge, badgeLp);

        TextView title = label("포켓몬카드 측정기", 27, white, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(10);
        root.addView(title, titleLp);

        TextView sub = label("카드를 촬영하면 정보를 확인하고 실시간 시세를 검색할 수 있습니다.", 13, Color.rgb(194, 205, 235), false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        subLp.bottomMargin = dp(12);
        root.addView(sub, subLp);

        FrameLayout cameraCard = new FrameLayout(this);
        cameraCard.setBackground(roundRect(panel, dp(24)));
        LinearLayout.LayoutParams cameraLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(540));
        cameraLp.bottomMargin = dp(12);
        root.addView(cameraCard, cameraLp);

        textureView = new TextureView(this);
        FrameLayout.LayoutParams full = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        full.setMargins(dp(5), dp(5), dp(5), dp(5));
        cameraCard.addView(textureView, full);

        capturedView = new ImageView(this);
        capturedView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        capturedView.setBackgroundColor(Color.rgb(10, 17, 42));
        capturedView.setVisibility(View.GONE);
        cameraCard.addView(capturedView, full);

        guideOverlay = new GuideOverlay(this);
        cameraCard.addView(guideOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusText = label("카메라 권한을 확인하는 중…", 13, Color.rgb(222, 231, 255), true);
        statusText.setGravity(Gravity.CENTER);
        statusText.setBackground(roundRect(Color.rgb(16, 27, 62), dp(14)));
        statusText.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.addView(statusText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        captureButton = primaryButton("카드 촬영", yellow, Color.rgb(20, 29, 58));
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        buttonLp.topMargin = dp(10);
        root.addView(captureButton, buttonLp);

        LinearLayout resultPanel = sectionPanel(root, "카드 정보", "촬영한 카드의 기본 정보를 확인하고 실시간 시세를 검색할 수 있습니다.");

        analyzeButton = primaryButton("다시 인식", Color.rgb(75, 118, 255), Color.WHITE);
        analyzeButton.setEnabled(false);
        LinearLayout.LayoutParams analyzeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        resultPanel.addView(analyzeButton, analyzeLp);

        helperText = label("카드를 인식하면 시세 검색을 사용할 수 있습니다.", 12, Color.rgb(137, 153, 196), false);
        helperText.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams helperLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helperLp.topMargin = dp(10);
        resultPanel.addView(helperText, helperLp);

        cardNameInput = labeledField(resultPanel, "카드명");
        setCodeInput = labeledField(resultPanel, "세트코드");
        cardNumberInput = labeledField(resultPanel, "카드번호");
        hpInput = labeledField(resultPanel, "HP");
        rarityInput = labeledField(resultPanel, "희귀도");
        rarityInput.setVisibility(View.GONE);
        languageInput = labeledField(resultPanel, "언어");

        confidenceText = label("인식 신뢰도: -", 13, Color.rgb(20, 34, 77), true);
        confidenceText.setBackground(roundRect(Color.rgb(236, 241, 255), dp(14)));
        confidenceText.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams confLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        confLp.topMargin = dp(12);
        resultPanel.addView(confidenceText, confLp);
        confidenceText.setVisibility(View.GONE);

        LinearLayout dbPanel = new LinearLayout(this);
        dbPanel.setOrientation(LinearLayout.VERTICAL);
        dbPanel.setPadding(dp(12), dp(12), dp(12), dp(12));
        dbPanel.setBackground(roundRect(Color.rgb(246, 248, 255), dp(18)));
        LinearLayout.LayoutParams dbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dbLp.topMargin = dp(14);
        resultPanel.addView(dbPanel, dbLp);
        dbPanel.setVisibility(View.GONE);

        TextView dbTitle = label("카드 DB 대조", 15, Color.rgb(20, 34, 77), true);
        dbPanel.addView(dbTitle);
        dbStatusText = label("아직 DB 대조 전입니다.", 13, Color.rgb(74, 86, 120), true);
        LinearLayout.LayoutParams dbStatusLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dbStatusLp.topMargin = dp(8);
        dbPanel.addView(dbStatusText, dbStatusLp);
        dbNameText = label("DB 카드명: -", 13, Color.rgb(74, 86, 120), false);
        dbSetText = label("DB 세트: -", 13, Color.rgb(74, 86, 120), false);
        dbNumberText = label("DB 카드번호: -", 13, Color.rgb(74, 86, 120), false);
        dbCategoryText = label("DB 종류: -", 13, Color.rgb(74, 86, 120), false);
        dbRarityText = label("DB 희귀도: -", 13, Color.rgb(74, 86, 120), false);
        dbPanel.addView(dbNameText);
        dbPanel.addView(dbSetText);
        dbPanel.addView(dbNumberText);
        dbPanel.addView(dbCategoryText);
        dbPanel.addView(dbRarityText);

                LinearLayout marketPanel = new LinearLayout(this);
        marketPanel.setOrientation(LinearLayout.VERTICAL);
        marketPanel.setPadding(dp(12), dp(12), dp(12), dp(12));
        marketPanel.setBackground(roundRect(Color.rgb(246, 248, 255), dp(18)));

        LinearLayout.LayoutParams marketLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        marketLp.topMargin = dp(14);
        resultPanel.addView(marketPanel, marketLp);

        TextView marketTitle =
                label("실시간 카드 시세", 15, Color.rgb(20, 34, 77), true);
        marketPanel.addView(marketTitle);

        marketStatusText =
                label("카드 인식 후 시세를 검색할 수 있습니다.",
                        13, Color.rgb(74, 86, 120), true);

        LinearLayout.LayoutParams marketStatusLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        marketStatusLp.topMargin = dp(8);
        marketPanel.addView(marketStatusText, marketStatusLp);

        marketButton =
                primaryButton(
                        "시세 검색",
                        Color.rgb(255, 216, 70),
                        Color.rgb(20, 29, 58)
                );
        marketButton.setEnabled(true);

        LinearLayout.LayoutParams marketButtonLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(54)
                );
        marketButtonLp.topMargin = dp(10);
        marketPanel.addView(marketButton, marketButtonLp);

        minPriceText =
                label("최저가: -", 15, Color.rgb(20, 34, 77), true);

        averagePriceText =
                label("평균가: -", 15, Color.rgb(20, 34, 77), true);

        maxPriceText =
                label("최고가: -", 15, Color.rgb(20, 34, 77), true);

        marketSampleText =
                label("검색 표본: -", 12, Color.rgb(74, 86, 120), false);

        marketListingsText =
                label("판매처: -", 12, Color.rgb(74, 86, 120), false);

        LinearLayout.LayoutParams priceLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        priceLp.topMargin = dp(10);

        marketPanel.addView(minPriceText, priceLp);
        marketPanel.addView(averagePriceText);
        marketPanel.addView(maxPriceText);
        marketPanel.addView(marketSampleText);
        marketPanel.addView(marketListingsText);
        TextView rawTitle = label("AI 원문 응답", 13, Color.rgb(20, 34, 77), true);
        LinearLayout.LayoutParams rawTitleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rawTitleLp.topMargin = dp(12);
        resultPanel.addView(rawTitle, rawTitleLp);
        rawTitle.setVisibility(View.GONE);

        aiRawText = label("아직 인식 전입니다.", 12, Color.rgb(74, 86, 120), false);
        aiRawText.setBackground(roundRect(Color.rgb(243, 246, 255), dp(16)));
        aiRawText.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams rawLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rawLp.topMargin = dp(6);
        resultPanel.addView(aiRawText, rawLp);
        aiRawText.setVisibility(View.GONE);
    }

    private LinearLayout sectionPanel(LinearLayout root, String title, String sub) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(roundRect(Color.WHITE, dp(22)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        root.addView(box, lp);

        TextView t = label(title, 18, Color.rgb(20, 34, 77), true);
        box.addView(t);
        TextView s = label(sub, 12, Color.rgb(94, 104, 134), false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(6);
        box.addView(s, slp);
        return box;
    }

    private Button primaryButton(String text, int bgColor, int textColor) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(17);
        b.setTextColor(textColor);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setBackground(roundRect(bgColor, dp(18)));
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.rgb(39, 68, 137));
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setBackground(roundRect(Color.rgb(237, 241, 255), dp(16)));
        return b;
    }

    private EditText labeledField(LinearLayout parent, String label) {
        TextView t = label(label, 13, Color.rgb(20, 34, 77), true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(12);
        parent.addView(t, tlp);
        return field(parent, label + " 입력 또는 자동입력");
    }

    private EditText field(LinearLayout parent, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(15);
        e.setTextColor(Color.rgb(20, 34, 77));
        e.setHintTextColor(Color.rgb(154, 163, 190));
        e.setBackground(roundRect(Color.rgb(243, 246, 255), dp(16)));
        e.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        parent.addView(e, lp);
        return e;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) ensurePermissionAndOpen();
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private final TextureView.SurfaceTextureListener surfaceListener = new TextureView.SurfaceTextureListener() {
        @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { ensurePermissionAndOpen(); }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    };

    private void ensurePermissionAndOpen() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            statusText.setText("카메라 권한이 필요합니다. 권한 요청 창에서 ‘허용’을 눌러주세요.");
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        } else {
            openCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusText.setText("권한 허용 완료 · 카메라를 시작합니다.");
                openCamera();
            } else {
                statusText.setText("카메라 권한이 거부되었습니다. 휴대폰 설정 → 앱 → 포켓몬카드 측정기 → 권한에서 카메라를 허용해 주세요.");
                captureButton.setEnabled(false);
            }
        }
    }

    private void openCamera() {
        if (!textureView.isAvailable() || backgroundHandler == null || cameraDevice != null) return;
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            cameraId = chooseBackCamera(manager);
            if (cameraId == null) {
                statusText.setText("후면 카메라를 찾지 못했습니다.");
                return;
            }
            CameraCharacteristics cc = manager.getCameraCharacteristics(cameraId);
            Integer so = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (so != null) sensorOrientation = so;
            StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
            previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class));
            Size jpegSize = chooseJpegSize(map.getOutputSizes(ImageFormat.JPEG));
            imageReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(this::onImageAvailable, backgroundHandler);
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
            statusText.setText("카메라 준비 중…");
            manager.openCamera(cameraId, cameraStateCallback, backgroundHandler);
        } catch (Exception e) {
            statusText.setText("카메라 시작 오류: " + e.getMessage());
        }
    }

    private String chooseBackCamera(CameraManager manager) throws CameraAccessException {
        String fallback = null;
        for (String id : manager.getCameraIdList()) {
            if (fallback == null) fallback = id;
            Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        return fallback;
    }

    private Size choosePreviewSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return new Size(1280, 720);
        List<Size> list = new ArrayList<>(Arrays.asList(sizes));
        Collections.sort(list, (a,b) -> Long.compare((long)b.getWidth()*b.getHeight(), (long)a.getWidth()*a.getHeight()));
        for (Size s : list) {
            long px = (long)s.getWidth() * s.getHeight();
            if (px <= 1920L * 1080L && s.getWidth() >= 1280) return s;
        }
        return list.get(list.size() - 1);
    }

    private Size chooseJpegSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) return new Size(1920, 1080);
        return Collections.max(Arrays.asList(sizes), Comparator.comparingLong(s -> (long)s.getWidth() * s.getHeight()));
    }

    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createPreviewSession();
        }
        @Override public void onDisconnected(CameraDevice camera) {
            camera.close(); cameraDevice = null;
        }
        @Override public void onError(CameraDevice camera, int error) {
            camera.close(); cameraDevice = null;
            runOnUiThread(() -> statusText.setText("카메라 오류 코드: " + error));
        }
    };

    private void createPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
                        runOnUiThread(() -> {
                            statusText.setText("카메라 준비 완료 · 카드 네 모서리를 노란 프레임에 맞춰주세요.");
                            captureButton.setEnabled(true);
                        });
                    } catch (CameraAccessException e) {
                        runOnUiThread(() -> statusText.setText("미리보기 시작 오류: " + e.getMessage()));
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    runOnUiThread(() -> statusText.setText("카메라 미리보기 구성에 실패했습니다."));
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            statusText.setText("미리보기 오류: " + e.getMessage());
        }
    }

    private void takePicture() {
        if (cameraDevice == null || captureSession == null || imageReader == null) {
            Toast.makeText(this, "카메라가 아직 준비되지 않았습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            CaptureRequest.Builder still = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            still.addTarget(imageReader.getSurface());
            still.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            still.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            still.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation());
            statusText.setText("촬영 중…");
            captureSession.capture(still.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            statusText.setText("촬영 오류: " + e.getMessage());
        }
    }

    private int jpegOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int deviceDegrees;
        switch (rotation) {
            case Surface.ROTATION_90: deviceDegrees = 90; break;
            case Surface.ROTATION_180: deviceDegrees = 180; break;
            case Surface.ROTATION_270: deviceDegrees = 270; break;
            default: deviceDegrees = 0;
        }
        return (sensorOrientation - deviceDegrees + 360) % 360;
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            lastPhoto = new File(getCacheDir(), "last_card.jpg");
            try (FileOutputStream out = new FileOutputStream(lastPhoto)) {
                out.write(bytes);
            }
            final Bitmap portraitBitmap = normalizeCapturedPhoto(lastPhoto);
            runOnUiThread(() -> {
                capturedView.setImageBitmap(portraitBitmap);
                capturedView.setVisibility(View.VISIBLE);
                textureView.setVisibility(View.INVISIBLE);
                guideOverlay.setVisibility(View.GONE);
                captureButton.setText("다시 촬영");
                analyzeButton.setEnabled(false);
                statusText.setText("촬영 완료 · 카드 정보를 확인하는 중입니다…");
                helperText.setTextColor(Color.rgb(75, 118, 255));
                helperText.setText("STEP 1 완료 ✓  · 서버로 안전하게 사진을 전송합니다.");
                analyzeCapturedCard();
            });
        } catch (Exception e) {
            runOnUiThread(() -> statusText.setText("사진 저장 오류: " + e.getMessage()));
        } finally {
            if (image != null) image.close();
        }
    }

    /**
     * Some Camera2 implementations save portrait captures as landscape JPEG pixels
     * with an EXIF rotation flag. BitmapFactory does not apply that flag by itself,
     * so normalize the file once after capture. The app always scans portrait cards,
     * therefore a landscape image with no useful EXIF flag is rotated 90 degrees.
     */
    private Bitmap normalizeCapturedPhoto(File file) throws Exception {
        int rotation = 0;
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
            else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
        } catch (Exception ignored) { }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (maxSide / sample > 2200) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) throw new Exception("촬영 이미지를 읽지 못했습니다.");

        // Fallback for devices that omit/ignore EXIF orientation on Camera2 JPEGs.
        if (rotation == 0 && bitmap.getWidth() > bitmap.getHeight()) rotation = 90;

        Bitmap normalized = bitmap;
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (normalized != bitmap) bitmap.recycle();
        }

        // Save the normalized portrait JPEG so the preview and AI server receive the same orientation.
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            normalized.compress(Bitmap.CompressFormat.JPEG, 94, out);
        }
        return normalized;
    }

    private void showCameraAgain() {
        capturedView.setVisibility(View.GONE);
        textureView.setVisibility(View.VISIBLE);
        guideOverlay.setVisibility(View.VISIBLE);
        captureButton.setText("카드 촬영");
        analyzeButton.setEnabled(false);
        statusText.setText("카드 네 모서리를 노란 프레임에 맞춰주세요.");
        if (dbStatusText != null) {
            dbStatusText.setText("아직 DB 대조 전입니다.");
            dbNameText.setText("DB 카드명: -");
            dbSetText.setText("DB 세트: -");
            dbNumberText.setText("DB 카드번호: -");
            dbCategoryText.setText("DB 종류: -");
            dbRarityText.setText("DB 희귀도: -");
        }
    }

    private void analyzeCapturedCard() {
        if (lastPhoto == null || !lastPhoto.exists()) {
            Toast.makeText(this, "먼저 카드를 촬영해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        analyzeButton.setEnabled(false);
        statusText.setText("카드 사진을 분석하는 중입니다…");
        aiRawText.setText("분석 중...");
        new Thread(() -> {
            try {
                JSONObject ai = callRecognitionServer(lastPhoto);
                runOnUiThread(() -> applyAiResult(ai));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    analyzeButton.setEnabled(true);
                    String msg = friendlyServerError(e.getMessage());
                    statusText.setText(msg);
                    aiRawText.setText("오류: " + e.getMessage());
                });
            }
        }).start();
    }

    private void applyAiResult(JSONObject obj) {
        cardNameInput.setText(obj.optString("cardName", ""));
        setCodeInput.setText(obj.optString("setCode", ""));
        cardNumberInput.setText(obj.optString("cardNumber", ""));
        hpInput.setText(obj.optString("hp", ""));
        rarityInput.setText(obj.optString("rarity", ""));
        languageInput.setText(obj.optString("language", ""));
        confidenceText.setText("인식 신뢰도: " + obj.optString("confidence", "-") + "%");
        aiRawText.setText(obj.toString());
        analyzeButton.setEnabled(true);
        statusText.setText("카드 인식 완료 · 정보를 확인하는 중입니다…");
        helperText.setTextColor(Color.rgb(75, 118, 255));
        helperText.setText("카드 정보 확인 중…");
        dbStatusText.setText("카드 정보 확인 중…"); 
        new Thread(() -> {
            try {
                JSONObject db = verifyCardWithDb(obj);
                runOnUiThread(() -> applyDbResult(db));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dbStatusText.setText("DB 대조 실패 · AI 결과는 그대로 유지합니다.");
                    dbNameText.setText("DB 오류: " + e.getMessage());
                    statusText.setText("카드 인식 완료 · 시세 검색을 사용할 수 있습니다.");
                    helperText.setText("카드 인식 완료 ✓  · 시세 검색 가능");
                });
            }
        }).start();
    }

    private void applyDbResult(JSONObject db) {
        boolean matched = db.optBoolean("matched", false);
        boolean corrected = db.optBoolean("corrected", false);
        String dbName = db.optString("name", "");
        String dbSet = db.optString("setName", "");
        String dbNumber = db.optString("cardNumber", "");
        String dbCategory = db.optString("category", "");
        String dbRarity = db.optString("rarity", "");
        String dbHp = db.optString("hp", "");

        dbNameText.setText("DB 카드명: " + emptyDash(dbName));
        dbSetText.setText("DB 세트: " + emptyDash(dbSet));
        dbNumberText.setText("DB 카드번호: " + emptyDash(dbNumber));
        dbCategoryText.setText("DB 종류: " + emptyDash(dbCategory));
        dbRarityText.setText("DB 희귀도: " + emptyDash(dbRarity));

        if (matched) {
            if (!dbRarity.isEmpty()) rarityInput.setText(dbRarity);
            if (!dbHp.isEmpty() && hpInput.getText().toString().trim().isEmpty()) hpInput.setText(dbHp);
            if (!dbNumber.isEmpty()) cardNumberInput.setText(dbNumber);
            dbStatusText.setText(corrected ? "DB 확인 완료 ✓ · 카드번호를 자동 보정했습니다." : "DB 확인 완료 ✓ · AI 결과와 DB가 일치합니다.");
            statusText.setText("카드 정보 확인 완료 · 시세를 검색해 주세요.");
            helperText.setTextColor(Color.rgb(255, 216, 70));
            helperText.setText("시세 검색 준비 완료 ✓");
            marketButton.setEnabled(true);
        } else {
            dbStatusText.setText(db.optString("message", "DB에서 완전 일치 카드를 찾지 못했습니다."));
            statusText.setText("카드 인식 완료 · 시세를 검색할 수 있습니다.");
            helperText.setText("카드 인식 완료 ✓  · 시세 검색 가능");
            marketButton.setEnabled(true);
        }
    }

        private void searchMarket() {
        String cardName = cardNameInput.getText().toString().trim();
        String language = languageInput.getText().toString().trim();
        String setCode = setCodeInput.getText().toString().trim();
        String cardNumber = cardNumberInput.getText().toString().trim();
        String rarity = rarityInput.getText().toString().trim();

        if (cardName.isEmpty() || cardNumber.isEmpty()) {
            Toast.makeText(this, "카드명과 카드번호를 먼저 확인해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        marketButton.setEnabled(false);
        marketStatusText.setText("국내 판매처에서 동일 카드 시세를 검색하는 중…");
        minPriceText.setText("최저가: 검색 중…");
        averagePriceText.setText("평균가: 검색 중…");
        maxPriceText.setText("최고가: 검색 중…");
        marketSampleText.setText("검색 표본: 확인 중…");
        marketListingsText.setText("판매처: 검색 중…");

        new Thread(() -> {
            try {
                JSONObject result = callMarketServer(
                        cardName,
                        language,
                        setCode,
                        cardNumber,
                        rarity
                );

                runOnUiThread(() -> applyMarketResult(result));

            } catch (Exception e) {
                runOnUiThread(() -> {
                    marketStatusText.setText("시세 검색 실패 · 잠시 후 다시 시도해 주세요.");
                    marketListingsText.setText("오류: " + e.getMessage());
                    minPriceText.setText("최저가: -");
                    averagePriceText.setText("평균가: -");
                    maxPriceText.setText("최고가: -");
                    marketSampleText.setText("검색 표본: -");
                    marketButton.setEnabled(true);
                });
            }
        }).start();
    }

    private JSONObject callMarketServer(
            String cardName,
            String language,
            String setCode,
            String cardNumber,
            String rarity
    ) throws Exception {

        JSONObject request = new JSONObject();
        request.put("cardName", cardName);
        request.put("language", language);
        request.put("setCode", setCode);
        request.put("cardNumber", cardNumber);
        request.put("rarity", rarity);

        URL url = new URL(SERVER_URL + "/market");
        HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
        );
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(180000);
        conn.setDoOutput(true);

        byte[] out = request.toString().getBytes("UTF-8");
        conn.setFixedLengthStreamingMode(out.length);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(out);
        }

        int code = conn.getResponseCode();

        String body = readAll(
                code >= 200 && code < 300
                        ? conn.getInputStream()
                        : conn.getErrorStream()
        );

        if (code < 200 || code >= 300) {
            String detail = body;

            try {
                JSONObject err = new JSONObject(body);
                detail = err.optString("error", body);
            } catch (Exception ignored) {
            }

            throw new Exception(
                    "시세 서버 응답 오류(" + code + "): " + detail
            );
        }

        JSONObject response = new JSONObject(body);

        if (!response.optBoolean("ok", false)) {
            throw new Exception(
                    response.optString(
                            "error",
                            "시세 검색에 실패했습니다."
                    )
            );
        }

        return response.getJSONObject("result");
    }

    private void applyMarketResult(JSONObject result) {
        int sampleCount = result.optInt("sampleCount", 0);

        if (sampleCount <= 0) {
            marketStatusText.setText(
                    "정확히 일치하는 국내 판매글을 찾지 못했습니다."
            );
            minPriceText.setText("최저가: -");
            averagePriceText.setText("평균가: -");
            maxPriceText.setText("최고가: -");
            marketSampleText.setText("검색 표본: 0건");
            marketListingsText.setText("판매처: -");
            marketButton.setEnabled(true);
            return;
        }

        int minPrice = result.optInt("minPrice", 0);
        int averagePrice = result.optInt("averagePrice", 0);
        int maxPrice = result.optInt("maxPrice", 0);

        minPriceText.setText(
                "최저가: " + formatWon(minPrice)
        );

        averagePriceText.setText(
                "평균가: " + formatWon(averagePrice)
        );

        maxPriceText.setText(
                "최고가: " + formatWon(maxPrice)
        );

        marketSampleText.setText(
                "검색 표본: " + sampleCount + "건"
        );

        JSONArray listings = result.optJSONArray("listings");

        StringBuilder sb = new StringBuilder();
        sb.append("판매처:");

        if (listings != null) {
            int count = Math.min(listings.length(), 5);

            for (int i = 0; i < count; i++) {
                JSONObject item = listings.optJSONObject(i);

                if (item == null) continue;

                String market = item.optString("market", "판매처");
                String title = item.optString("title", "");
                int price = item.optInt("priceKRW", 0);

                sb.append("\n")
                        .append("• ")
                        .append(market)
                        .append(" · ")
                        .append(formatWon(price));

                if (!title.isEmpty()) {
                    sb.append("\n  ").append(title);
                }
            }
        }

        marketListingsText.setText(sb.toString());

        marketStatusText.setText(
                "시세 검색 완료 ✓ · 동일 카드 "
                        + sampleCount
                        + "건 기준"
        );

        marketButton.setEnabled(true);
    }

    private String formatWon(int value) {
        if (value <= 0) return "-";
        return String.format("%,d원", value);
    }
    private String emptyDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s;
    }

    private JSONObject verifyCardWithDb(JSONObject ai) throws Exception {
        String setCode = ai.optString("setCode", "").trim();
        String aiName = ai.optString("cardName", "").trim();
        String aiNumber = ai.optString("cardNumber", "").trim();
        String aiLanguage = ai.optString("language", "").trim().toLowerCase();
        String localId = aiNumber.contains("/") ? aiNumber.substring(0, aiNumber.indexOf('/')).trim() : aiNumber;
        localId = normalizeLocalId(localId);

        boolean koreanCard = aiLanguage.contains("korean") || aiLanguage.equals("ko") || containsHangul(aiName);

        // Korean cards must be verified against Korean data first.
        // Japanese/English printings can use different numbering, so never accept a
        // Japanese/English card by NUMBER ONLY when the photographed card is Korean.
        if (koreanCard) {
            JSONObject koreanSet = fetchDbJson("https://api.tcgdex.net/v2/ko/sets/" + URLEncoder.encode(setCode, "UTF-8"));
            if (koreanSet != null) {
                JSONArray cards = koreanSet.optJSONArray("cards");
                if (cards != null) {
                    JSONObject byName = findCardByName(cards, aiName);
                    JSONObject byNumber = findCardByLocalId(cards, localId);

                    if (byName != null) {
                        String dbLocal = normalizeLocalId(byName.optString("localId", ""));
                        JSONObject full = fetchDbJson("https://api.tcgdex.net/v2/ko/sets/" + URLEncoder.encode(setCode, "UTF-8") + "/" + URLEncoder.encode(dbLocal, "UTF-8"));
                        if (full == null) full = byName;

                        // If both name and number agree, it is a strong Korean DB match.
                        boolean sameNumber = dbLocal.equals(localId);
                        String message = sameNumber
                                ? "한국판 DB에서 카드명과 번호가 일치합니다."
                                : "한국판 DB에서 카드명은 확인됐지만 번호가 달라 DB 번호로 보정합니다.";
                        return buildDbResult(full, koreanSet, dbLocal, true, !sameNumber, "ko", message);
                    }

                    if (byNumber != null) {
                        JSONObject full = fetchDbJson("https://api.tcgdex.net/v2/ko/sets/" + URLEncoder.encode(setCode, "UTF-8") + "/" + URLEncoder.encode(localId, "UTF-8"));
                        if (full == null) full = byNumber;

                        String dbName = full.optString("name", "");
                        if (!dbName.isEmpty() && normalizeName(dbName).equals(normalizeName(aiName))) {
                            return buildDbResult(full, koreanSet, localId, true, false, "ko",
                                    "한국판 DB에서 카드명과 번호가 일치합니다.");
                        }

                        JSONObject result = buildDbResult(full, koreanSet, localId, false, false, "ko",
                                "한국판 DB에 같은 번호는 있지만 카드명까지 확인되지 않아 AI 결과를 유지합니다.");
                        return result;
                    }
                }
            }

            JSONObject none = new JSONObject();
            none.put("matched", false);
            none.put("corrected", false);
            none.put("language", "ko");
            none.put("message", "한국판 우선 검증: 한국어 DB에서 완전 일치를 찾지 못했습니다. 일본판/영문판은 카드번호가 다를 수 있어 번호만으로 대체하지 않고 AI 결과를 유지합니다.");
            return none;
        }

        // Non-Korean cards may use language fallbacks, but a match is accepted only
        // when the card name matches. Number-only candidates are shown as unverified.
        JSONObject fallback = null;
        String[] langs = new String[]{"ja", "en"};
        for (String lang : langs) {
            JSONObject set = fetchDbJson("https://api.tcgdex.net/v2/" + lang + "/sets/" + URLEncoder.encode(setCode, "UTF-8"));
            if (set == null) continue;
            JSONArray cards = set.optJSONArray("cards");
            if (cards == null) continue;

            JSONObject byName = findCardByName(cards, aiName);
            JSONObject byNumber = findCardByLocalId(cards, localId);

            if (byName != null) {
                String dbLocal = normalizeLocalId(byName.optString("localId", ""));
                JSONObject full = fetchDbJson("https://api.tcgdex.net/v2/" + lang + "/sets/" + URLEncoder.encode(setCode, "UTF-8") + "/" + URLEncoder.encode(dbLocal, "UTF-8"));
                if (full == null) full = byName;
                return buildDbResult(full, set, dbLocal, true, !dbLocal.equals(localId), lang, "");
            }

            if (byNumber != null && fallback == null) {
                JSONObject full = fetchDbJson("https://api.tcgdex.net/v2/" + lang + "/sets/" + URLEncoder.encode(setCode, "UTF-8") + "/" + URLEncoder.encode(localId, "UTF-8"));
                if (full == null) full = byNumber;
                fallback = buildDbResult(full, set, localId, false, false, lang,
                        "번호 후보는 찾았지만 카드명이 일치하지 않아 자동 확정하지 않습니다.");
            }
        }

        if (fallback != null) return fallback;
        JSONObject none = new JSONObject();
        none.put("matched", false);
        none.put("corrected", false);
        none.put("message", "카드 DB에서 카드명과 번호가 함께 일치하는 항목을 찾지 못했습니다. AI 결과를 유지합니다.");
        return none;
    }

    private boolean containsHangul(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= 0xAC00 && ch <= 0xD7A3) || (ch >= 0x3131 && ch <= 0x318E)) return true;
        }
        return false;
    }

    private JSONObject buildDbResult(JSONObject card, JSONObject set, String localId, boolean matched, boolean corrected, String lang, String message) throws Exception {
        JSONObject out = new JSONObject();
        int official = set.optJSONObject("cardCount") != null ? set.optJSONObject("cardCount").optInt("official", 0) : 0;
        String displayNumber = localId;
        if (official > 0) displayNumber = padNumber(localId, official) + "/" + official;
        out.put("matched", matched);
        out.put("corrected", corrected);
        out.put("language", lang);
        out.put("name", card.optString("name", ""));
        out.put("setName", set.optString("name", ""));
        out.put("cardNumber", displayNumber);
        out.put("category", card.optString("category", ""));
        out.put("rarity", card.optString("rarity", ""));
        out.put("hp", card.has("hp") ? String.valueOf(card.opt("hp")) : "");
        out.put("image", card.optString("image", ""));
        out.put("message", message);
        return out;
    }

    private JSONObject findCardByLocalId(JSONArray cards, String target) {
        String nTarget = normalizeLocalId(target);
        for (int i = 0; i < cards.length(); i++) {
            JSONObject c = cards.optJSONObject(i);
            if (c == null) continue;
            if (normalizeLocalId(c.optString("localId", "")).equalsIgnoreCase(nTarget)) return c;
        }
        return null;
    }

    private JSONObject findCardByName(JSONArray cards, String target) {
        String nTarget = normalizeName(target);
        if (nTarget.isEmpty()) return null;
        JSONObject contains = null;
        for (int i = 0; i < cards.length(); i++) {
            JSONObject c = cards.optJSONObject(i);
            if (c == null) continue;
            String n = normalizeName(c.optString("name", ""));
            if (n.equals(nTarget)) return c;
            if (n.length() >= 3 && nTarget.length() >= 3 && (n.contains(nTarget) || nTarget.contains(n))) contains = c;
        }
        return contains;
    }

    private String normalizeName(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private String normalizeLocalId(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.matches("\\d+")) {
            try { return String.valueOf(Integer.parseInt(t)); } catch (Exception ignored) { }
        }
        return t;
    }

    private String padNumber(String localId, int official) {
        if (!localId.matches("\\d+")) return localId;
        int digits = String.valueOf(official).length();
        try { return String.format("%0" + digits + "d", Integer.parseInt(localId)); } catch (Exception e) { return localId; }
    }

    private JSONObject fetchDbJson(String urlText) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlText).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(18000);
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        if (code == 404) return null;
        String body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        if (code < 200 || code >= 300) throw new Exception("TCGdex 응답 오류(" + code + ")");
        return new JSONObject(body);
    }

    private JSONObject callRecognitionServer(File imageFile) throws Exception {
        byte[] imageBytes = prepareImageBytes(imageFile);
        JSONObject request = new JSONObject();
        request.put("imageBase64", Base64.encodeToString(imageBytes, Base64.NO_WRAP));
        request.put("mimeType", "image/jpeg");

        URL url = new URL(SERVER_URL + "/analyze");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);
        byte[] out = request.toString().getBytes("UTF-8");
        conn.setFixedLengthStreamingMode(out.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(out);
        }

        int code = conn.getResponseCode();
        String body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
        if (code < 200 || code >= 300) {
            String detail = body;
            try {
                JSONObject err = new JSONObject(body);
                detail = err.optString("error", body);
            } catch (Exception ignored) { }
            throw new Exception("서버 응답 오류(" + code + "): " + detail);
        }

        JSONObject resp = new JSONObject(body);
        if (!resp.optBoolean("ok", false)) {
            throw new Exception(resp.optString("error", "카드 분석에 실패했습니다."));
        }
        return resp.getJSONObject("result");
    }

    private String friendlyServerError(String raw) {
        if (raw == null) return "카드 인식 중 오류가 발생했습니다. 다시 시도해 주세요.";
        String lower = raw.toLowerCase();
        if (raw.contains("429") || lower.contains("quota") || lower.contains("billing")) {
            return "현재 카드 분석 요청이 많습니다. 잠시 후 다시 시도해 주세요.";
        }
        if (raw.contains("401") || lower.contains("api key") || lower.contains("unauthorized")) {
            return "카드 분석 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "카드 분석이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.";
        }
        return "카드 인식 실패 · 다시 인식 버튼으로 재시도해 주세요.";
    }

    private byte[] prepareImageBytes(File file) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) throw new Exception("이미지 파일을 읽지 못했습니다.");
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int max = Math.max(width, height);
        Bitmap send = bitmap;
        if (max > 1400) {
            float scale = 1400f / max;
            send = Bitmap.createScaledBitmap(bitmap, Math.round(width * scale), Math.round(height * scale), true);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        send.compress(Bitmap.CompressFormat.JPEG, 88, baos);
        if (send != bitmap) send.recycle();
        bitmap.recycle();
        return baos.toByteArray();
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void closeCamera() {
        try { if (captureSession != null) captureSession.close(); } catch (Exception ignored) { }
        captureSession = null;
        try { if (cameraDevice != null) cameraDevice.close(); } catch (Exception ignored) { }
        cameraDevice = null;
        try { if (imageReader != null) imageReader.close(); } catch (Exception ignored) { }
        imageReader = null;
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CardCamera");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try { backgroundThread.join(); } catch (InterruptedException ignored) { }
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    public static class GuideOverlay extends View {
        private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF card = new RectF();
        private final float density;

        public GuideOverlay(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            cornerPaint.setColor(Color.rgb(255, 216, 70));
            cornerPaint.setStrokeWidth(5 * density);
            cornerPaint.setStyle(Paint.Style.STROKE);
            cornerPaint.setStrokeCap(Paint.Cap.ROUND);
            shadePaint.setColor(Color.argb(85, 3, 8, 27));
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(13 * density);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float frameW = w * 0.90f;
            float frameH = frameW * (88f / 63f);
            if (frameH > h * 0.88f) { frameH = h * 0.88f; frameW = frameH * (63f / 88f); }
            float left = (w - frameW) / 2f;
            float top = (h - frameH) / 2f - 8 * density;
            card.set(left, top, left + frameW, top + frameH);

            c.drawRect(0, 0, w, card.top, shadePaint);
            c.drawRect(0, card.bottom, w, h, shadePaint);
            c.drawRect(0, card.top, card.left, card.bottom, shadePaint);
            c.drawRect(card.right, card.top, w, card.bottom, shadePaint);

            float L = 38 * density;
            drawCorner(c, card.left, card.top, +L, +L);
            drawCorner(c, card.right, card.top, -L, +L);
            drawCorner(c, card.left, card.bottom, +L, -L);
            drawCorner(c, card.right, card.bottom, -L, -L);

            String text = "카드 모서리를 프레임에 맞춰주세요";
            float tw = textPaint.measureText(text);
            float tx = (w - tw) / 2f;
            float ty = Math.min(h - 20 * density, card.bottom + 32 * density);
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(Color.argb(180, 10, 20, 55));
            RectF pill = new RectF(tx - 11*density, ty - 20*density, tx + tw + 11*density, ty + 8*density);
            c.drawRoundRect(pill, 14*density, 14*density, bg);
            c.drawText(text, tx, ty, textPaint);
        }

        private void drawCorner(Canvas c, float x, float y, float dx, float dy) {
            c.drawLine(x, y, x + dx, y, cornerPaint);
            c.drawLine(x, y, x, y + dy, cornerPaint);
        }
    }
}
