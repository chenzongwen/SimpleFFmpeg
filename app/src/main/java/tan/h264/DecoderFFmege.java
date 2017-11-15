package tan.h264;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import android.view.View;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;


public class DecoderFFmege extends View implements Runnable {
    FFmpegNative ffmpeg;
    I420Frame decoderFrame;
    VideoStreamsView vsv;

    int width = 640;
    int height = 480;

    byte[] mPixel = new byte[width * height * 2];

    ByteBuffer buffer = ByteBuffer.wrap(mPixel);
    Bitmap videoBit = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

    String PathFileName;

    public DecoderFFmege(Context context, int width, int height) {
        super(context);
        setFocusable(true);

        this.width = width;
        this.height = height;

        int i = mPixel.length;
        for (i = 0; i < mPixel.length; i++) {
            mPixel[i] = (byte) 0x00;
        }

        ffmpeg = new FFmpegNative();
    }

    public void startListen() {
        new Thread(this).start();
    }

    long renderTime = 0;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        renderTime = System.currentTimeMillis();

        videoBit.copyPixelsFromBuffer(buffer);   //makeBuffer(data565, N));
        buffer.position(0);

        canvas.scale(2f,2f);
        canvas.drawBitmap(videoBit, 0, 0, null);

        Log.e("eee", "render Time: " + String.valueOf(System.currentTimeMillis() - renderTime));
    }

    byte[] sockBuf = new byte[width * height * 10];
    byte[] nalBuf = new byte[width * height * 10];

    int mTrans = 0x0F0F0F0F;
    private final int PORT = 8088;
    private ServerSocket server;

    long lastTime = 0;
    long decoderTime = 0;
    long totalDecoderTime = 0;
    long findHeadTime = 0;
    long totalTime = 0;
    long socketReadTime = 0;
    long totalSocketReadTime = 0;

    boolean bFirstDecode = true;

    ByteBuffer outDecoderRGBBuffer = ByteBuffer.allocateDirect(width * height * 10);
    ByteBuffer outDecoderYUVBuffer = ByteBuffer.allocateDirect(width * height * 10);
    ByteBuffer firstFrameBuffer = ByteBuffer.allocateDirect(width * height * 10);
    ByteBuffer nalBuffer = ByteBuffer.allocateDirect(width * height * 10);

    int count = 0;

    @Override
    public void run() {

        ffmpeg.decode_init();  //解码器初始化

        //创建TCP Server 接收视频流
        Socket client;
        DataInputStream is = null;
        try {
            server = new ServerSocket(PORT);
            client = server.accept();
            client.setReceiveBufferSize(500000);
            client.setSendBufferSize(500000);
            client.setTcpNoDelay(true);
            client.setPerformancePreferences(0, 0, 1);
            is = new DataInputStream(client.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }


        int bytesRead = 0;
        int nalBufUsed = 0;
        int sockBufUsed = 0;
        int nalLen;
        boolean bFirst = true;
        boolean bFindPPS = false;
        int PPSLength = 0;
        boolean bFindSPS = false;
        int SPSLength = 0;
        byte[] SPS_PPS = new byte[100];

        int FPS = 0;


        while (true) {
            count++;
            try {
                socketReadTime = System.currentTimeMillis();

                totalTime = System.currentTimeMillis();

                bytesRead = is.read(sockBuf);

                totalSocketReadTime += System.currentTimeMillis() - socketReadTime;
                //Log.e("eee", "socket Read Time: " + String.valueOf(System.currentTimeMillis() - socketReadTime));

                if (bytesRead != -1) {
                    sockBufUsed = 0;

                    while (bytesRead - sockBufUsed > 0) {
                        findHeadTime = System.currentTimeMillis();
                        nalLen = MergeBuffer(nalBuf, nalBufUsed, sockBuf, sockBufUsed, bytesRead - sockBufUsed);
                        //Log.e("eee", "nalLen: "+String.valueOf(nalLen));
                        nalBufUsed += nalLen;
                        sockBufUsed += nalLen;

                        //Log.e("eee", "Find Head Time: " + String.valueOf(System.currentTimeMillis() - findHeadTime));

                        while (mTrans == 1) {
                            mTrans = 0xFFFFFFFF;

                            if (bFirst == true) // the first start flag
                            {
                                bFirst = false;
                            } else  // a complete NAL data, include 0x00000001 trail.
                            {
                                if (bFindPPS == false || bFindSPS == false) // true
                                {
                                    if ((nalBuf[4] & 0x1F) == 7) {
                                        bFindSPS = true;

                                        System.arraycopy(nalBuf, 0, SPS_PPS, 0, nalBufUsed - 4);
                                        SPSLength = nalBufUsed - 4;
                                    }

                                    if ((nalBuf[4] & 0x1F) == 8) {
                                        bFindPPS = true;

                                        System.arraycopy(nalBuf, 0, SPS_PPS, SPSLength, nalBufUsed - 4);
                                        PPSLength = nalBufUsed - 4;
                                    }

                                    nalBuf[0] = 0;
                                    nalBuf[1] = 0;
                                    nalBuf[2] = 0;
                                    nalBuf[3] = 1;

                                    nalBufUsed = 4;

                                    break;
                                }

                                decoderTime = System.currentTimeMillis();

                                int gotFrame = 0;
                                if (bFirstDecode) {
                                    bFirstDecode = false;

                                    byte[] sps_pps_IDR = new byte[SPSLength + PPSLength + nalBufUsed - 4 ];
                                    System.arraycopy(SPS_PPS, 0, sps_pps_IDR, 0, SPSLength + PPSLength);
                                    System.arraycopy(nalBuf, 0, sps_pps_IDR, SPSLength + PPSLength, nalBufUsed - 4 );    //将接收到的第一帧NAL 与 SPS PPS 拼合，送入解码器

                                    firstFrameBuffer.clear();
                                    firstFrameBuffer.put(sps_pps_IDR);

                                    gotFrame = ffmpeg.decodeFrame2(firstFrameBuffer, sps_pps_IDR.length);
                                } else {

                                    byte[] outBuffer = new byte[nalBufUsed - 4];
                                    System.arraycopy(nalBuf, 0, outBuffer, 0, nalBufUsed - 4);

                                    nalBuffer.clear();
                                    nalBuffer.put(outBuffer);

                                    gotFrame = ffmpeg.decodeFrame2(nalBuffer, nalBufUsed - 4);   //将接收到的NAL 送入解码器
                                }

                                if (gotFrame > 0) {
                                    //RGB565视频格式
                                    outDecoderRGBBuffer.clear();
                                    int length = ffmpeg.copyFrameRGB(outDecoderRGBBuffer);    //将解码出来的RGB565数据拷贝到 outDecoderRGBBuffer

                                    outDecoderRGBBuffer.limit(length);
                                    outDecoderRGBBuffer.get(mPixel, 0, outDecoderRGBBuffer.limit());
                                    postInvalidate();


                                    //YUV420p视频格式
//                                    outDecoderYUVBuffer.clear();
//                                    int length = ffmpeg.copyFrameYUV420p(outDecoderYUVBuffer);    //将解码出来的YUV420p数据拷贝到 outDecoderYUVBuffer

                                    FPS++;
                                    totalDecoderTime += System.currentTimeMillis() - decoderTime;
                                    //Log.e("eee", "Decoder Time: " + String.valueOf(System.currentTimeMillis() - decoderTime));
                                }
                            }

                            nalBuf[0] = 0;
                            nalBuf[1] = 0;
                            nalBuf[2] = 0;
                            nalBuf[3] = 1;

                            nalBufUsed = 4;

                            if (System.currentTimeMillis() - lastTime >= 1000) {
                                //Log.e("eee", String.valueOf(System.currentTimeMillis() - lastTime) + " FPS: " + String.valueOf(FPS));

//                                Log.e("eee", "totalSocketReadTime "+String.valueOf(totalSocketReadTime/FPS));
//                                Log.e("eee", "totalDecoderTime "+String.valueOf(totalDecoderTime/FPS));
                                totalDecoderTime = 0;
                                totalSocketReadTime = 0;

                                lastTime = System.currentTimeMillis();
                                FPS = 0;


                            }
                        }
                    }
                }
                Log.e("eee", "Total Time: " + String.valueOf(System.currentTimeMillis() - totalTime));

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    int MergeBuffer(byte[] nalBuf, int nalBufUsed, byte[] sockBuf, int sockBufUsed, int sockRemain) {
        int i = 0;
        byte Temp;

        for (i = 0; i < sockRemain; i++) {
            Temp = sockBuf[i + sockBufUsed];
            nalBuf[i + nalBufUsed] = Temp;

            mTrans <<= 8;
            mTrans |= Temp;

            if (mTrans == 1)  //找到起始码
            {
                //findNumTime(sockBuf, i);
                i++;
                break;
            }
        }

        return i;
    }

    long recNum = 0;
    long recTime = 0;

    public void findNumTime(byte[] socketBuffer, int index) {
        if (index > 16) {

            byte[] time = new byte[8];
            time[0] = socketBuffer[index - 11];
            time[1] = socketBuffer[index - 10];
            time[2] = socketBuffer[index - 9];
            time[3] = socketBuffer[index - 8];
            time[4] = socketBuffer[index - 7];
            time[5] = socketBuffer[index - 6];
            time[6] = socketBuffer[index - 5];
            time[7] = socketBuffer[index - 4];

            recTime = byteToLong(time);
            Log.e("time", String.valueOf(recTime - System.currentTimeMillis()));

            byte[] num = new byte[8];
            num[0] = socketBuffer[index - 19];
            num[1] = socketBuffer[index - 18];
            num[2] = socketBuffer[index - 17];
            num[3] = socketBuffer[index - 16];
            num[4] = socketBuffer[index - 15];
            num[5] = socketBuffer[index - 14];
            num[6] = socketBuffer[index - 13];
            num[7] = socketBuffer[index - 12];

            recNum = byteToLong(num);
            Log.e("num", String.valueOf(recNum - count));
        }
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
}
