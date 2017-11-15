package tan.h264;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Debug;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;


public class EncoderH264 implements Runnable {
    private static final String TAG = "TanH264";

    Context context;

    public EncoderH264(Context context, SharedPreferences sharedPreferences, String videoPath, int bitRate) {

        this.context = context;
        this.sharedPreferences = sharedPreferences;

        fd = videoPath;
        this.bitRate = bitRate;
    }

    //本地Socket,用于传输摄像头视频流到编码器
    LocalServerSocket lss;
    LocalSocket receiver, sender;

    public void InitLocalSocket() {
        try {
            lss = new LocalServerSocket("H26422");
            receiver = new LocalSocket();

            receiver.connect(new LocalSocketAddress("H26422"));
            receiver.setReceiveBufferSize(500000);
            receiver.setSendBufferSize(500000);

            sender = lss.accept();
            sender.setReceiveBufferSize(500000);
            sender.setSendBufferSize(500000);

        } catch (IOException e) {
            Log.e(TAG, e.toString());
//            this.finish();
            return;
        }
    }


    private MediaRecorder mMediaRecorder = null;
    private int videoWidth = 640;
    private int videoHeight = 480;
    private int videoRate = 30;
    private int bitRate = 1500000;
    private Camera camera = null;
    Camera.Parameters p;


    private SurfaceHolder mSurfaceHolder;
    private boolean mMediaRecorderRecording = false;

    public void setSurfaceHole(SurfaceHolder mSurfaceHolder) {
        this.mSurfaceHolder = mSurfaceHolder;
    }

    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
            Camera.Parameters p = c.getParameters();

            p.setRecordingHint(true);
            if (p.isVideoStabilizationSupported()) {
                p.setVideoStabilization(true);
            }
            c.setParameters(p);
        } catch (Exception e) {
            e.printStackTrace();
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private String fd = "data/data/tan.h264/h264.3gp";

    public boolean initializeVideo() {
        if (mSurfaceHolder == null) {
            return false;
        }

        mMediaRecorderRecording = true;

        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        } else {
            mMediaRecorder.reset();
        }

        camera = getCameraInstance();
        camera.unlock();
        mMediaRecorder.setCamera(camera);

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoFrameRate(videoRate);
        mMediaRecorder.setVideoEncodingBitRate(bitRate);   //BitRate 1000kbps
        mMediaRecorder.setVideoSize(videoWidth, videoHeight);

        mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
        mMediaRecorder.setMaxDuration(0);
        mMediaRecorder.setMaxFileSize(0);
        if (SPS == null) {
            mMediaRecorder.setOutputFile(fd);
        } else {
            mMediaRecorder.setOutputFile(sender.getFileDescriptor());
        }

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

    private void releaseCamera() {
        if (camera != null) {
            camera.release();        // release the camera for other applications
            camera = null;
        }
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            if (mMediaRecorderRecording) {
                mMediaRecorder.stop();
                mMediaRecorderRecording = false;
            }
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            camera.lock();
        }
        Debug.stopMethodTracing();
    }


    public void startVideoRecording() {
        new Thread(this).start();
    }

    private final int MAXFRAMEBUFFER = 20480 * 1000;//20K
    private byte[] h264frame = new byte[MAXFRAMEBUFFER];
    private final byte[] head = new byte[]{0x00, 0x00, 0x00, 0x01};   //NALU 头部起始识别码 00 00 00 01
    private RandomAccessFile file_test;

    private Socket sendClient = null;
    private OutputStream sendOutStream = null;

    public boolean bFirstFrame = true;
    boolean bSendisc = false;
    long send_byte_total = 0;
    byte[] head_sps_pps;

    String serverIP = "127.0.0.1";
    int port = 8088;

    public void setServerIP(String IP, int port) {
        serverIP = IP;
        this.port = port;
    }

    long lastTime = 0;
    long readTime = 0;
    long sendTime = 0;
    long readSizeTime = 0;
    int FPS = 0;

    public void run() {
        try {

            //avcC box H264的设置参数
            //esds box MPEG_4_SP 的设置参数
            //其实 如果分辨率 等数值不变的话，这些参数是不会变化的，
            //那么我就只需要在第一次运行的时候确定就可以了

            if (SPS == null) {
                Log.e(TAG, "Rlease MediaRecorder and get SPS and PPS");
                Thread.sleep(100);
                releaseMediaRecorder();
                readSPSAndPPSfromVideoFile();
                Thread.sleep(100);
                initializeVideo();
            }

            //存储编码后视频流
//            try {
//                final String path = Environment.getExternalStorageDirectory().getPath() + "/H264";
//                String videoPath = path + "/encoder.h264";
//                File file = new File(videoPath);
//                if (file.exists())
//                    file.delete();
//                file_test = new RandomAccessFile(file, "rw");
//            } catch (Exception ex) {
//                Log.v("System.out", ex.toString());
//            }

//            file_test.write(head);
//            file_test.write(SPS);//write sps
//            file_test.write(head);
//            file_test.write(PPS);//write pps


            byte[] sps = byteMerger(head, SPS);
            byte[] pps = byteMerger(head, PPS);
            head_sps_pps = byteMerger(sps, pps);    //拼合sps和pps,并存储起来

            //建立TCP Client，并连接服务器
            sendClient = new Socket();
            InetSocketAddress isa = new InetSocketAddress(serverIP.replace("/", ""), port);
            sendClient.connect(isa, 2000);
            sendClient.setSendBufferSize(500000);
            sendClient.setReceiveBufferSize(500000);
            sendClient.setPerformancePreferences(0, 0, 1);
            bSendisc = sendClient.isConnected();
            sendClient.setTcpNoDelay(true);
            sendClient.setKeepAlive(true);
            sendOutStream = sendClient.getOutputStream();

            int h264length = 0;
            DataInputStream dataInput = new DataInputStream(receiver.getInputStream());

            findMdat(dataInput);
            int count = 0;
            while (mMediaRecorderRecording) {

                h264length = dataInput.readInt();          //读取每场的长度
                //Log.e("eee", "h264length: " + String.valueOf(h264length));

                ReadSize(h264length, dataInput);

                byte[] h264 = new byte[h264length];
                System.arraycopy(h264frame, 0, h264, 0, h264length);
                byte[] head_h264 = byteMerger(head, h264); //在每个完整NAL前面添加起始识别码

                //添加帧时序
                //Log.e("eee", "SendNum: " + String.valueOf(rate));
//                count++;
//                byte[] numbyte = longToByte(count);
//                byte[] curtime = longToByte(System.currentTimeMillis());
//                byte[] numandtime = byteMerger(numbyte, curtime);
//                byte[] data = byteMerger(numandtime, head_h264);


                sendTime = System.currentTimeMillis();

                if (bSendisc) {
                    if (bFirstFrame) {
                        bFirstFrame = false;
                        try {
                            byte[] f = byteMerger(head_sps_pps, head_h264);   //视频第一帧，将 head SPS head PPS head NAL(IDR) 拼合一起发出去
                            sendOutStream.write(f, 0, f.length);
                            sendOutStream.flush();
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            sendOutStream.write(head_h264, 0, head_h264.length);
                            sendOutStream.flush();

                            FPS++;
                            //Log.e("eee", "Send Time: " + String.valueOf(System.currentTimeMillis() - sendTime));

                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }

                if (System.currentTimeMillis() - lastTime >= 1000) {
                    //Log.e("eee", "FPS: " + String.valueOf(FPS));
                    FPS = 0;
                    lastTime = System.currentTimeMillis();
                }
            }
            file_test.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int byteArray2int(byte[] b) {
        byte[] a = new byte[4];
        int i = a.length - 1, j = b.length - 1;
        for (; i >= 0; i--, j--) {//从b的尾部(即int值的低位)开始copy数据
            if (j >= 0)
                a[i] = b[j];
            else
                a[i] = 0;//如果b.length不足4,则将高位补0
        }
        int v0 = (a[0] & 0xff) << 24;//&0xff将byte值无差异转成int,避免Java自动类型提升后,会保留高位的符号位
        int v1 = (a[1] & 0xff) << 16;
        int v2 = (a[2] & 0xff) << 8;
        int v3 = (a[3] & 0xff);
        return v0 + v1 + v2 + v3;
    }

    //long类型转成byte数组
    public static byte[] longToByte(long number) {
        long temp = number;
        byte[] b = new byte[8];
        for (int i = 0; i < b.length; i++) {
            b[i] = new Long(temp & 0xff).byteValue();// 将最低位保存在最低位
            temp = temp >> 8; // 向右移8位
        }
        return b;
    }


    //byte数组转成long
    public static long byteToLong(byte[] b) {

        long s = 0;
        long s0 = b[0] & 0xff;// 最低位
        long s1 = b[1] & 0xff;
        long s2 = b[2] & 0xff;
        long s3 = b[3] & 0xff;
        long s4 = b[4] & 0xff;// 最低位
        long s5 = b[5] & 0xff;
        long s6 = b[6] & 0xff;
        long s7 = b[7] & 0xff;

        // s0不变
        s1 <<= 8;
        s2 <<= 16;
        s3 <<= 24;
        s4 <<= 8 * 4;
        s5 <<= 8 * 5;
        s6 <<= 8 * 6;
        s7 <<= 8 * 7;
        s = s0 | s1 | s2 | s3 | s4 | s5 | s6 | s7;

        return s;

    }

    //java 合并两个byte数组
    public static byte[] byteMerger(byte[] byte_1, byte[] byte_2) {
        byte[] byte_3 = new byte[byte_1.length + byte_2.length];
        System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);
        System.arraycopy(byte_2, 0, byte_3, byte_1.length, byte_2.length);
        return byte_3;
    }

    private void ReadSize(int h264length, DataInputStream dataInput) throws IOException, InterruptedException {
        int read = 0;
        int temp = 0;
        while (read < h264length) {
            temp = dataInput.read(h264frame, read, h264length - read);
            //Log.e(TAG, String.format("h264frame %d,%d,%d", h264length, read, h264length - read));
            if (temp == -1) {
                Log.e(TAG, "no data get wait for data coming.....");
                Thread.sleep(10);
                continue;
            }
            read += temp;
        }
    }

    private void findMdat(DataInputStream in) throws FileNotFoundException {

        final byte[] mdat = new byte[]{0x6D, 0x64, 0x61, 0x74};
        for (int i = 0; i < 640 * 480 * 10; i++) {
            try {
                if (in.readByte() == mdat[0]) {
                    byte[] next3Byte = new byte[3];
                    in.read(next3Byte, 0, 3);
                    if (next3Byte[0] == mdat[1] && next3Byte[1] == mdat[2] && next3Byte[2] == mdat[3]) {
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public byte[] SPS;
    private byte[] PPS;
    private int StartMdatPlace = 0;

    SharedPreferences sharedPreferences;

    public int getSPSAndPPS() {
        StartMdatPlace = sharedPreferences.getInt(
                String.format("%d%d.sps", videoWidth, videoHeight), -1);

        if (StartMdatPlace != -1) {
            byte[] temp = new byte[100];
            try {
                FileInputStream file_in = context.openFileInput(
                        String.format("%d%d.sps", videoWidth, videoHeight));

                int index = 0;
                int read = 0;
                while (true) {
                    read = file_in.read(temp, index, 10);
                    if (read == -1) break;
                    else index += read;
                }
                Log.e(TAG, "sps length:" + index);
                SPS = new byte[index];
                System.arraycopy(temp, 0, SPS, 0, index);

                file_in.close();

                index = 0;
                //read PPS
                file_in = context.openFileInput(
                        String.format("%d%d.pps", videoWidth, videoHeight));
                while (true) {
                    read = file_in.read(temp, index, 10);
                    if (read == -1) break;
                    else index += read;
                }
                Log.e(TAG, "pps length:" + index);
                PPS = new byte[index];
                System.arraycopy(temp, 0, PPS, 0, index);
            } catch (FileNotFoundException e) {
                //e.printStackTrace();
                Log.e(TAG, e.toString());
            } catch (IOException e) {
                //e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        } else {
            SPS = null;
            PPS = null;
        }
        return StartMdatPlace;
    }

    public void readSPSAndPPSfromVideoFile() throws Exception {
        File file = new File(fd);
        FileInputStream fileInput = new FileInputStream(file);

        int length = (int) file.length();
        byte[] data = new byte[length];

        fileInput.read(data);

        final byte[] mdat = new byte[]{0x6D, 0x64, 0x61, 0x74};
        final byte[] avcc = new byte[]{0x61, 0x76, 0x63, 0x43};

        for (int i = 0; i < length; i++) {
            if (data[i] == mdat[0] && data[i + 1] == mdat[1] && data[i + 2] == mdat[2] && data[i + 3] == mdat[3]) {
                StartMdatPlace = i + 4;//find mdat
                Log.e("ee", String.valueOf(i));
                break;
            }
        }
        Log.e(TAG, "StartMdatPlace:" + StartMdatPlace);
        //
        String mdatStr = String.format("mdata_%d%d.mdat", videoWidth, videoHeight);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(mdatStr, StartMdatPlace);
        editor.commit();

        for (int i = 0; i < length; i++) {
            if (data[i] == avcc[0] && data[i + 1] == avcc[1] && data[i + 2] == avcc[2] && data[i + 3] == avcc[3]) {
                int sps_start = i + 3 + 7;//����i+3ָ��avcc��c���ټ�7���6λAVCDecoderConfigurationRecord����

                // 加7的目的是为了跳过
                // (1)8字节的 configurationVersion
                // (2)8字节的 AVCProfileIndication
                // (3)8字节的 profile_compatibility
                // (4)8 字节的 AVCLevelIndication
                // (5)6 bit 的 reserved
                // (6)2 bit 的 lengthSizeMinusOne
                // (7)3 bit 的 reserved
                // (8)5 bit 的numOfSequenceParameterSets
                // 共6个字节，然后到达sequenceParameterSetLength的位置

                //sps length and sps data
                byte[] sps_3gp = new byte[2];//sps length
                sps_3gp[1] = data[sps_start];
                sps_3gp[0] = data[sps_start + 1];
                int sps_length = bytes2short(sps_3gp);
                Log.e(TAG, "sps_length :" + sps_length);

                sps_start += 2;//skip length  跳过2个字节的 sequenceParameterSetLength
                SPS = new byte[sps_length];
                System.arraycopy(data, sps_start, SPS, 0, sps_length);
                //save sps
                FileOutputStream file_out = context.openFileOutput(
                        String.format("%d%d.sps", videoWidth, videoHeight),
                        Context.MODE_PRIVATE);
                file_out.write(SPS);
                file_out.close();

                //pps length and pps data  spsStartPos + spsLength 可以跳到pps位置，再加1的目的是跳过1字节的 numOfPictureParameterSets
                int pps_start = sps_start + sps_length + 1;
                byte[] pps_3gp = new byte[2];
                pps_3gp[1] = data[pps_start];
                pps_3gp[0] = data[pps_start + 1];
                int pps_length = bytes2short(pps_3gp);
                Log.e(TAG, "PPS LENGTH:" + pps_length);

                pps_start += 2;

                PPS = new byte[pps_length];
                System.arraycopy(data, pps_start, PPS, 0, pps_length);


                //Save PPS
                file_out = context.openFileOutput(
                        String.format("%d%d.pps", videoWidth, videoHeight),
                        Context.MODE_PRIVATE);
                file_out.write(PPS);
                file_out.close();
                break;
            }
        }
    }

    public short bytes2short(byte[] b) {
        short mask = 0xff;
        short temp = 0;
        short res = 0;
        for (int i = 0; i < 2; i++) {
            res <<= 8;
            temp = (short) (b[1 - i] & mask);
            res |= temp;
        }
        return res;
    }
}