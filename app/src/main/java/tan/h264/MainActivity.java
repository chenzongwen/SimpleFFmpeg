package tan.h264;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;

public class MainActivity extends Activity implements SurfaceHolder.Callback {

    Button startBtn, record;
    TextView textViewIP;

    DecoderFFmege decoderView;
    EncoderH264 encoderH264;
    SurfaceView surfaceView;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        InitDecoder(640,480);
        InitEncoder();

        startBtn = (Button) findViewById(R.id.startBtn);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                textViewIP = (TextView) findViewById(R.id.idText);
                String serverIP = textViewIP.getText().toString();

                encoderH264.setSurfaceHole(mSurfaceHolder);
                encoderH264.setServerIP(serverIP, 8088);
                encoderH264.InitLocalSocket();
                if (encoderH264.getSPSAndPPS() == -1) {
                    try {
                        encoderH264.readSPSAndPPSfromVideoFile();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                encoderH264.initializeVideo();
                encoderH264.startVideoRecording();
            }
        });

        record = (Button) findViewById(R.id.recordVideo);
        record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String path = Environment.getExternalStorageDirectory().getPath() + "/H264";
                String videoPath=path+"/test.mp4";
                File file=new File(path);
                if(!file.exists())
                {
                    file.mkdirs();
                }
                RecordVideo rec = new RecordVideo(mSurfaceHolder, videoPath);
                rec.record();
                rec.startReleaseThread();
            }
        });

    }
    private void InitEncoder()
    {
        InitMediaSharePreference();
        final String path = Environment.getExternalStorageDirectory().getPath() + "/H264";
        String videoPath=path+"/test.mp4";
        File file=new File(path);
        if(!file.exists())
        {
            file.mkdirs();
        }
        encoderH264 = new EncoderH264(this, sharedPreferences, videoPath,2000000);
        InitSurfaceView();
    }
    private void InitDecoder(int width,int height)
    {
        decoderView = new DecoderFFmege(this, width, height); //初始化解码类
        decoderView.startListen();       //开启线程，等待接收视频流

        //设置解码显示画面资源
        RelativeLayout.LayoutParams ringLayoutParams =
                new RelativeLayout.LayoutParams(2000, 1500);
        ((RelativeLayout) findViewById(R.id.h264viewContainer)).addView(decoderView, ringLayoutParams);
    }


    SharedPreferences sharedPreferences;
    private final String mediaShare = "media";

    private void InitMediaSharePreference() {
        sharedPreferences = this.getSharedPreferences(mediaShare, MODE_PRIVATE);
    }

    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;

    public void InitSurfaceView() {
        mSurfaceView = (SurfaceView) findViewById(R.id.cameraPreview);
        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback((SurfaceHolder.Callback) this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurfaceView.setVisibility(View.VISIBLE);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        mSurfaceHolder = holder;
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // TODO Auto-generated method stub
        Debug.stopMethodTracing();
    }
}
