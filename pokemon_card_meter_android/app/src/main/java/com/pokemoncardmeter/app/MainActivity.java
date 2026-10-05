package com.pokemoncardmeter.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Point;
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
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int CAMERA_PERMISSION = 1001;

    private TextureView textureView;
    private GuideOverlay guideOverlay;
    private ImageView capturedView;
    private TextView statusText;
    private TextView step2Text;
    private Button captureButton;

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
    }

    private void buildUi() {
        int navy = Color.rgb(8, 17, 47);
        int panel = Color.rgb(20, 34, 77);
        int yellow = Color.rgb(255, 216, 70);
        int white = Color.WHITE;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(navy);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        setContentView(root);

        TextView badge = label("STEP 1 · CAMERA", 12, Color.rgb(23, 33, 63), true);
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

        TextView sub = label("앱 안에서 카드를 프레임에 맞춰 촬영해 주세요.", 13, Color.rgb(194, 205, 235), false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        subLp.bottomMargin = dp(12);
        root.addView(sub, subLp);

        FrameLayout cameraCard = new FrameLayout(this);
        cameraCard.setBackground(roundRect(panel, dp(24)));
        LinearLayout.LayoutParams cameraLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
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

        captureButton = new Button(this);
        captureButton.setAllCaps(false);
        captureButton.setText("카드 촬영");
        captureButton.setTextSize(17);
        captureButton.setTextColor(Color.rgb(20, 29, 58));
        captureButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        captureButton.setBackground(roundRect(yellow, dp(18)));
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        buttonLp.topMargin = dp(10);
        root.addView(captureButton, buttonLp);

        step2Text = label("다음 단계: 촬영한 사진을 ChatGPT로 인식하여 카드 정보를 자동 입력", 12, Color.rgb(137, 153, 196), false);
        step2Text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stepLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stepLp.topMargin = dp(10);
        root.addView(step2Text, stepLp);
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

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

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
            runOnUiThread(() -> {
                capturedView.setImageBitmap(BitmapFactory.decodeFile(lastPhoto.getAbsolutePath()));
                capturedView.setVisibility(View.VISIBLE);
                textureView.setVisibility(View.INVISIBLE);
                guideOverlay.setVisibility(View.GONE);
                captureButton.setText("다시 촬영");
                statusText.setText("촬영 완료 · 사진이 앱 안에 저장되었습니다.");
                step2Text.setTextColor(Color.rgb(255, 216, 70));
                step2Text.setText("STEP 1 완료 ✓  · 다음: 이 사진을 ChatGPT에 보내 카드정보 자동입력");
            });
        } catch (Exception e) {
            runOnUiThread(() -> statusText.setText("사진 저장 오류: " + e.getMessage()));
        } finally {
            if (image != null) image.close();
        }
    }

    private void showCameraAgain() {
        capturedView.setVisibility(View.GONE);
        textureView.setVisibility(View.VISIBLE);
        guideOverlay.setVisibility(View.VISIBLE);
        captureButton.setText("카드 촬영");
        statusText.setText("카드 네 모서리를 노란 프레임에 맞춰주세요.");
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
            float frameW = Math.min(w * 0.78f, h * 0.59f);
            float frameH = frameW * (88f / 63f);
            if (frameH > h * 0.79f) { frameH = h * 0.79f; frameW = frameH * (63f / 88f); }
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
