package org.chou;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class MainUI {
    private JPanel Form1;
    private JButton openFileBtn;
    private JLabel fileInfo;
    private JTextField rtmpText;
    private JButton liveBtn;

    private File mediaFile;

    public MainUI() {
        openFileBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser("D:/");
                int state = fileChooser.showOpenDialog(Form1);
                if (state == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    mediaFile = file;
                    fileInfo.setText("你選擇的檔案路徑:\t" + file.getPath());
                }
            }
        });
        liveBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    goLive();
                } catch (FrameGrabber.Exception ex) {
                    System.out.println("FrameGrabber ERROR");
                } catch (FrameRecorder.Exception | InterruptedException ex) {
                    System.out.println("FrameRecorder ERROR");
                }
            }
        });
    }


    private void goLive() throws FrameGrabber.Exception, FrameRecorder.Exception, InterruptedException {
        // ffmpeg log
        avutil.av_log_set_level(avutil.AV_LOG_ERROR);
        FFmpegLogCallback.set();

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(mediaFile.getAbsoluteFile());

        grabber.start(true);

        long startTime = System.currentTimeMillis();

        AVFormatContext avFormatContext = grabber.getFormatContext();

        // 檢查媒體軌
        if (avFormatContext.nb_streams() > 1) {
            // 取得影片 frame number
            int frameRate = (int) grabber.getVideoFrameRate();
            for ( int idx = 0; idx < avFormatContext.nb_streams(); idx++ ) {
                AVStream avStream = avFormatContext.streams(idx);
                AVCodecParameters avCodecParameters = avStream.codecpar();
                System.out.println(
                        "stream idx: " + idx + "\t編碼器: " + avCodecParameters.codec_type()
                                + "\t 解碼器 ID:" + avCodecParameters.codec_id()
                );
            }

            /**
             * 下面是媒體資訊 每一個 frame 的資料
             */

            int frameWidth = grabber.getImageWidth();
            int frameHeight = grabber.getImageHeight();
            int audioChannels = grabber.getAudioChannels();

            System.out.println("媒體寬:" + frameWidth + "\t高:" + frameHeight + "\t音訊通道數量:" + audioChannels);

            /**
             * 串流遠端主機初始化
             */
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(
                    rtmpText.getText(),
                    frameWidth,
                    frameHeight,
                    audioChannels
            );
            System.out.println("串流位置：" + rtmpText.getText());

            // 編碼器
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            // 格式
            recorder.setFormat("flv");
            // frame 數
            recorder.setFrameRate(frameRate);
            recorder.setGopSize(frameRate);
            // audio channel
            recorder.setAudioChannels(audioChannels);

            recorder.start();

            /**
             * 推送到遠端主機 （針對每一個影片中的 frame 去推送）
             */

            Frame frame;
            long videoTS = 0;
            int videoFrameNum = 0;
            int audioFrameNum = 0;
            int dataFrameNum = 0;

            // 假设一秒钟15帧，那么两帧间隔就是(1000/15)毫秒
            long interVal = 1000/frameRate;
            // 发送完一帧后sleep的时间，不能完全等于(1000/frameRate)，不然会卡顿，
            // 要更小一些，这里取八分之一
            interVal/=8;

            while (null != (frame = grabber.grab())) {
                videoTS = 1000 * (System.currentTimeMillis() - startTime);

                // timestamp
                recorder.setTimestamp(videoTS);

                // 有图像，就把视频帧加一
                if (null!=frame.image) {
                    videoFrameNum++;
                }

                // 有声音，就把音频帧加一
                if (null!=frame.samples) {
                    audioFrameNum++;
                }

                // 有数据，就把数据帧加一
                if (null!=frame.data) {
                    dataFrameNum++;
                }

                // 取出來的每一個 frame 推到遠端主機
                recorder.record(frame);

                // 等一下
                Thread.sleep(interVal);
            }

            System.out.println("推送完成！ 耗費時間：" + (System.currentTimeMillis() - startTime) / 1000 + "音频帧:" + audioFrameNum );

            /**
             * 釋放資源
             */
            recorder.close();
            grabber.close();
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("MainUI");
        frame.setContentPane(new MainUI().Form1);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }
}
