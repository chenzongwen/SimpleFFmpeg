package tan.h264;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.view.SurfaceHolder;

import java.io.IOException;


public class RecordVideo implements Runnable {
    private MediaRecorder mMediaRecorder = null;
    private SurfaceHolder mSurfaceHolder;
    private boolean isRecording = false;

    String videoPath;

    public RecordVideo(SurfaceHolder mSurfaceHolder, String videoPath) {
        this.mSurfaceHolder = mSurfaceHolder;
        this.videoPath = videoPath;
    }

    public void record() {

        if (isRecording) {
            // stop recording and release camera
            releaseMediaRecorder(); // release the MediaRecorder object
            camera.lock();         // take camera access back from MediaRecorder
            // inform the user that recording has stopped
            //setCaptureButtonText("Capture");
            isRecording = false;
        } else {
            // initialize video camera
            if (initializeVideo()) {
                // Camera is available and unlocked, MediaRecorder is prepared,
                // now you can start recording

                //setParameters();

                // inform the user that recording has started
                //setCaptureButtonText("Stop");
                isRecording = true;
            } else {
                // prepare didn't work, release the camera
                releaseMediaRecorder();
                // inform user
            }
        }
    }

    private Camera camera;
    Camera.Parameters p;

    private boolean initializeVideo() {
        if (mSurfaceHolder == null) {
            return false;
        }
        camera = getCameraInstance();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        } else {
            mMediaRecorder.reset();
        }

        camera.unlock();

        mMediaRecorder.setCamera(camera);

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoEncodingBitRate(1500000);   //BitRate 1000kbps
        mMediaRecorder.setVideoSize(640, 480);

        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
        mMediaRecorder.setMaxDuration(0);
        mMediaRecorder.setMaxFileSize(0);

        mMediaRecorder.setOutputFile(videoPath);


        try {
            mMediaRecorder.prepare();
            mMediaRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            releaseMediaRecorder();
        }

        return true;
    }

    private static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
            Camera.Parameters p=c.getParameters();
            p.setRecordingHint(true);
            if(p.isVideoStabilizationSupported())
            {
                p.setVideoStabilization(true);
            }
            c.setParameters(p);

        } catch (Exception e) {
            e.printStackTrace();
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    public void releaseMediaRecorder() {
        if (mMediaRecorder != null) {

            mMediaRecorder.stop();  // stop the recording
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            releaseCamera();
        }
        //Debug.stopMethodTracing();
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.release();        // release the camera for other applications
            camera = null;
        }
    }

    public void startReleaseThread() {
        new Thread(this).start();
    }

    @Override
    public void run() {
        long lastTime = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - lastTime > 8000) {
                releaseMediaRecorder();
                break;
            }
        }

    }
}
