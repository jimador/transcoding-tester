package com.transcoding.tester;

import org.bytedeco.javacpp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by james on 5/9/2016.
 * This is a really ugly class pulled from a C example 
 * @ https://github.com/mpenkov/ffmpeg-tutorial/blob/master/tutorial01.c
 * 
 * TODO: If I feel like it, clean this up, this is unreadable nonsense.
 * TODO: That's what I get for following a C project line for line
 *
 */
public class ConvertSample {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConvertSample.class);
    /**
     * Save individual video frames
     */
    public static void saveFrame(avutil.AVFrame avFrame, int width, int height, int frameNum){
        try(OutputStream stream = new FileOutputStream("frame" + frameNum + ".ppm")) {
            stream.write(("P6\n" + width + " " + height + "\n255\n").getBytes());

            // Write pixel data
            BytePointer data = avFrame.data(0);
            byte[] bytes = new byte[width * 3];
            int l = avFrame.linesize(0);
            for(int y = 0; y < height; y++) {
                data.position(y * l).get(bytes);
                stream.write(bytes);
            }
        } catch (IOException ex){
            LOGGER.error("Couldn't write to file " + ex);
        }
    }

    public static void main(String args[]){
        avformat.AVFormatContext avFormatContext = new avformat.AVFormatContext(null);
        int videoStream = -1;
        avcodec.AVCodecContext avCodecContext;
        avcodec.AVCodec avCodec;

        avutil.AVFrame avFrame, avFrameRgb;

        avcodec.AVPacket avPacket = new avcodec.AVPacket();
        int[] frameFinished = new int[1];
        int numBytes;
        BytePointer buffer;

        avutil.AVDictionary optionsDictionary = null;
        swscale.SwsContext swsContext;

        //Did you pass me something to convert?
        if(args.length < 1){
            LOGGER.error("No video found");
            System.exit(-1);
        }

        //register the muxers, demuxers, etc for all supported formats
        avformat.av_register_all();

        //Open file and store it on context
        if (avformat.avformat_open_input(avFormatContext, args[0], null, null) != 0) {
            LOGGER.error("Couldn't open file!");
            System.exit(-1);
        }

        if (avformat.avformat_find_stream_info(avFormatContext, (PointerPointer)null) < 0){
            LOGGER.error("Can't get stream info!");
            System.exit(-1);
        }

        //Dump information about the file onto standard error
        avformat.av_dump_format(avFormatContext, 0, args[0], 0);

        //Looking for the first video stream
        for(int i = 0; i < avFormatContext.nb_streams(); i++){
            if (avFormatContext.streams(i).codec().codec_type() == avutil.AVMEDIA_TYPE_VIDEO){
                videoStream = i;
                break;
            }
        }

        if(videoStream == -1){
            LOGGER.error("Can't find a video stream");
            System.exit(-1);
        }

        //Get a pointer to the codec context for the video stream
        avCodecContext = avFormatContext.streams(videoStream).codec();

        avCodec = avcodec.avcodec_find_decoder(avCodecContext.codec_id());
        if(avCodec == null){
            LOGGER.error("Unsupported Codec");
            System.exit(-1);
        }

        //Open the codec
        if (avcodec.avcodec_open2(avCodecContext, avCodec, optionsDictionary) < 0){
            LOGGER.error("Can't open codec");
            System.exit(-1);
        }

        //Allocation the video frame
        avFrame = avutil.av_frame_alloc();

        //Allocation the AVFrame structure
        avFrameRgb = avutil.av_frame_alloc();

        if (avFrameRgb == null){
            LOGGER.error("avFrameRgb is NULL!");
            System.exit(-1);
        }

        //Determine the required buffer size and allocate it
        numBytes = avcodec.avpicture_get_size(avutil.AV_PIX_FMT_RGB24, avCodecContext.width(), avCodecContext.height());
        buffer = new BytePointer(avutil.av_malloc(numBytes));

        swsContext = swscale.sws_getContext(avCodecContext.width(),
                avCodecContext.height(),
                avCodecContext.pix_fmt(),
                avCodecContext.width(),
                avCodecContext.height(),
                avutil.AV_PIX_FMT_RGB24,
                swscale.SWS_BILINEAR,
                null,
                null,
                (DoublePointer)null);

        //Assign appropriate parts of buffer to image planes in pFrameRGB
        //Note that pFrameRGB is an AVFrame, but AVFrame is a superset
        //of AVPicture
        avcodec.avpicture_fill(new avcodec.AVPicture(avFrameRgb),
                buffer,
                avutil.AV_PIX_FMT_RGB24,
                avCodecContext.width(),
                avCodecContext.height());

        int temp = 0;

        while (avformat.av_read_frame(avFormatContext, avPacket) >= 0){
            if(avPacket.stream_index() == videoStream){
                //Decode video frame
                avcodec.avcodec_decode_video2(avCodecContext, avFrame, frameFinished, avPacket);

                //Did we get a video frame?
                if(frameFinished[0] != 0){
                    swscale.sws_scale(swsContext,
                            avFrame.data(),
                            avFrame.linesize(),
                            0,
                            avCodecContext.height(),
                            avFrameRgb.data(),
                            avFrameRgb.linesize());

                    //Save the frame to disk
                    if( temp++ > 25 && temp++ < 50){
                        saveFrame(avFrameRgb, avCodecContext.width(), avCodecContext.height(), temp);
                    }
                }
            }

            //Free the packet that was allocated by av_read_frame
            avcodec.av_free_packet(avPacket);
        }

        //Free and close
        avutil.av_free(buffer);
        avutil.av_free(avFrame);
        avutil.av_free(avFrameRgb);
        avutil.av_free(avCodecContext);
        avformat.avformat_close_input(avFormatContext);

        System.exit(0);
    }
}
