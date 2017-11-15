package tan.h264;

import java.nio.ByteBuffer;


public class FFmpegNative {
    static {
        System.loadLibrary("avutil-54");
        System.loadLibrary("swresample-1");
        System.loadLibrary("avcodec-56");
        System.loadLibrary("avformat-56");
        System.loadLibrary("swscale-3");
        System.loadLibrary("avfilter-5");
        System.loadLibrary("avdevice-56");
        System.loadLibrary("ffmpeg_codec");
    }

    public native int decode_init();

    public native int decode_file(String filePath);

    public native int decodeFrame(ByteBuffer in, int inSzie);

    public native int decodeFrame2(ByteBuffer in, int inSzie);

    public native int copyFrameRGB(ByteBuffer out);

    public native int copyFrameYUV420p(ByteBuffer out);

    public native int copyFrame2(ByteBuffer outY, ByteBuffer outU, ByteBuffer outV);
}


class I420Frame {
    public int width;
    public int height;
    public int[] yuvStrides;
    public ByteBuffer[] yuvPlanes;
    public boolean yuvFrame;
    public int textureId;
}