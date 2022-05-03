package com.example.ar_core_plane;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    GLSurfaceView mSurfaceView;
    MainRenderer mainRenderer;
    TextView myTextView;

    Session session;
    Config mConfig;

    DisplayListener displayListener = new DisplayListener();
    CallBack callBack = new CallBack();

    float mCurrentX, mCurrentY;

    boolean userRequestedInstall = true, mTouched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        noTitleBar();
        setContentView(R.layout.activity_main);

        mSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);
        myTextView = (TextView) findViewById(R.id.textView);

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if(displayManager != null)
            displayManager.registerDisplayListener(displayListener, null);

        mainRenderer = new MainRenderer(callBack, this);
        settingSurfaceView();




    }

    @Override
    protected void onPause() {
        super.onPause();

        mSurfaceView.onPause();
        session.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requstCameraPermission();
        try {
            if (session == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, userRequestedInstall)) {
                    case INSTALLED:
                        session = new Session(this);
                        Log.d("메인", "ARCroe Session get도다제");
                        break;
                    default:
                        Log.d("메인", "AR Core의 설치가 필요해");
                        userRequestedInstall = false;
                        break;
                }
            }
        } catch (Exception e) {
        }


        mConfig = new Config(session);
        session.configure(mConfig);
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        mSurfaceView.onResume();
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    class CallBack implements MainRenderer.RenderCallBack{

        @Override
        public void preRender() {
            if(mainRenderer.mViewport){
                Display display = getWindowManager().getDefaultDisplay();
                int displayRotation = display.getRotation();
                mainRenderer.updateSession(session, displayRotation); // 화면회전된걸 보고 돕니당
            }
            session.setCameraTextureName(mainRenderer.getTextureId());
            Frame frame = null;

            try {
                frame = session.update();
            } catch (CameraNotAvailableException e) {
                e.printStackTrace();
            }
            if(frame.hasDisplayGeometryChanged())
                mainRenderer.mCamera.transformDisplayGeometry(frame);

            PointCloud pointCloud = frame.acquirePointCloud();
            mainRenderer.mPointCloud.update(pointCloud);
            pointCloud.release();

            if(mTouched){
                List<HitResult> results = frame.hitTest(mCurrentX, mCurrentY);
                for(HitResult result : results){
                    Pose pose = result.getHitPose(); // 증강공간에서의 좌표
                    float[] modelMatrix = new float[16];
                    pose.toMatrix(modelMatrix,0); // 좌표를 가지고 matrix 화 함
                    // 증강공간에서의 좌표에 객체가 있는지 받아온다.
                   Trackable trackable = result.getTrackable();

                   // 좌표에 걸린 객체가 Plane 인가?
                   if(trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(pose)) {
                       // 큐브의 modelMatrix를 터치한 증강현실 modelMatrix로 설정
//                       mainRenderer.mCube.setModelMatrix(modelMatrix);
                       mainRenderer.mObj.setModelMatrix(modelMatrix);
                   }
                }
                mTouched = false;
            }

            // Session으로부터 증강현실 속에서의 평면이나, 점 객체를 얻을 수 있다.
            //                              Plane       Point
            Collection<Plane> planes = session.getAllTrackables(Plane.class);

            boolean isPlaneDatected = false;

            for(Plane plane : planes){
                if(plane.getTrackingState() == TrackingState.TRACKING && plane.getSubsumedBy() == null){
                    mainRenderer.mPlane.update(plane); // 랜더링에서 plane 정보를 갱신하여 출력
                    isPlaneDatected = true;
                }

            }
            if(isPlaneDatected){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        myTextView.setText("평면 찾았습니다.");
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        myTextView.setText("평면 못 찾았습니다.");
                    }
                });
            }

            Camera camera = frame.getCamera();
            float[] projMatrix = new float[16];
            camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f);
            float[] viewMatrix = new float[16];
            camera.getViewMatrix(viewMatrix, 0);

            mainRenderer.setPorojectMatrix(projMatrix);
            mainRenderer.updateViewMatrix(viewMatrix);
        }
    }

    class DisplayListener implements DisplayManager.DisplayListener{
        @Override
        public void onDisplayAdded(int i) { }
        @Override
        public void onDisplayRemoved(int i) {  }
        @Override
        public void onDisplayChanged(int i) {
            synchronized (this){
                mainRenderer.mViewport = true;
            }
        }
    }

    private void settingSurfaceView(){
        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mSurfaceView.setRenderer(mainRenderer);
    }

    private void noTitleBar() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void requstCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 0);
    }

    public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_DOWN){
            mTouched = true;
            mCurrentX = event.getX();
            mCurrentY = event.getY();

        }
        return true;
    }
}