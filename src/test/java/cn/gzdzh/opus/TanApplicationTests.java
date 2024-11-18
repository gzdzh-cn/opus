package cn.gzdzh.opus;

import org.concentus.*;
import lombok.extern.slf4j.Slf4j;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusInfo;
import org.gagravarr.opus.OpusTags;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static cn.gzdzh.opus.ProgramController.BytesToShorts;

@Slf4j
@SpringBootTest
class TanApplicationTests {

    @Test
    void tranOpus() {

       String inputFilePath = "/Volumes/disk/site/java/demo/tanwav/input.bin";
       String outputPath = "/Volumes/disk/site/java/demo/tanwav";
//        if (inputFilePath.isEmpty() || outputPath.isEmpty()) {
//            return ResponseEntity.badRequest().body("请提供输入文件路径和输出路径");
//        }
        try {
            // 读取设置文件
            Path settingsPath = Paths.get("opus_setting.txt");
            if (!Files.exists(settingsPath)) {
                log.warn("设置文件未找到");
//                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("设置文件未找到");
            }
            String settingsLine = Files.readAllLines(settingsPath).get(1).trim();
            String[] settings = settingsLine.split(",");
            int type1Channel = Integer.parseInt(settings[0].trim()); //1 单声道
            int type1Framerate = Integer.parseInt(settings[1].trim()); //160000 采样率
            int type1EncFrameLen = Integer.parseInt(settings[2].trim()); //40  编码帧长
            int type1DecFrameLen = Integer.parseInt(settings[3].trim()); //320 解码帧长
            String type1Name = settings[4].trim(); //opus_dec
            log.info("settings = {}", (Object) settings);
            // 初始化 Opus 解码器
            OpusDecoder decoder = new OpusDecoder(type1Framerate, type1Channel);

            // 设置 WAV 文件参数
            AudioFormat audioFormat = new AudioFormat(type1Framerate, 16, type1Channel, true, false);
            ByteArrayOutputStream decodedOutput = new ByteArrayOutputStream();

            // 获取当前时间并格式化
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            // 创建输出 opus 文件
            String outputFilename = outputPath + "/" + timestamp + "_" + type1Name + ".wav";
            try (FileInputStream fileIn = new FileInputStream(inputFilePath);
            ) {

                byte[] inBuf = new byte[type1EncFrameLen];
                long start = System.currentTimeMillis();
                int bytesRead;
                while ((bytesRead = fileIn.read(inBuf)) != -1) {
                    if (bytesRead < type1EncFrameLen) {
                        break;
                    }
                    byte[] pcm = new byte[type1DecFrameLen * 2  * type1Channel];
                    int samplesDecoded = decoder.decode(inBuf, 0, bytesRead, pcm, 0, type1DecFrameLen, false);

                    if (samplesDecoded > 0) {
                        // 将解码的数据写入输出流
                        decodedOutput.write(pcm, 0, samplesDecoded * 2  * type1Channel);
                    }
                }

                //  创建 WAV 文件
                try (AudioInputStream finalAudioInputStream = new AudioInputStream(
                        new ByteArrayInputStream(decodedOutput.toByteArray()),
                        audioFormat,
                        decodedOutput.size() / audioFormat.getFrameSize());
                     FileOutputStream fileOutputStream = new FileOutputStream(outputFilename)) {
                    AudioSystem.write(finalAudioInputStream, AudioFileFormat.Type.WAVE, fileOutputStream);
                    System.out.println("处理完成, 输出文件: " + outputFilename);
                }

                long end = System.currentTimeMillis();
                System.out.println("Time was " + (end - start) + "ms");
                System.out.println("Done!");
            }
            System.out.println("处理完成: " + outputFilename);
        } catch (Exception e) {
            log.error("处理错误: {}", e.getMessage());
        }
    }

    /*
    * wav转opus
    * */
    @Test
    void tranWavToOpus() {
        String inputFilePath = "/Volumes/disk/site/java/demo/tanwav/input.wav";
        String outputPath = "/Volumes/disk/site/java/demo/tanwav";
        try {
            int type1Channel = 1;
            int type1Framerate = 16000; // 采样率
            int type1EncFrameLen = 40; // 编码帧长
            int type1DecFrameLen = 320; // 解码帧长

            OpusEncoder encoder = new OpusEncoder(type1Framerate, type1Channel, OpusApplication.OPUS_APPLICATION_AUDIO);
            encoder.setBitrate(96000);
//            encoder.setForceMode(OpusMode.MODE_CELT_ONLY);
            encoder.setSignalType(OpusSignal.OPUS_SIGNAL_MUSIC);
            encoder.setComplexity(10);
            // 获取当前时间并格式化
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            // 创建输出 opus 文件
            String outputFilename = outputPath + "/" + timestamp + ".opus";
            long start = System.currentTimeMillis();
            try (FileInputStream fileIn = new FileInputStream(inputFilePath);FileOutputStream fileOut = new FileOutputStream(outputFilename)){
//                FileInputStream fileIn = new FileInputStream(inputFilePath);
//                FileOutputStream fileOut = new FileOutputStream(outputFilename);
                OpusInfo info = new OpusInfo();
                info.setNumChannels(type1Channel); // 单声道
                info.setSampleRate(type1Framerate);
                OpusTags tags = new OpusTags();
                OpusFile file = new OpusFile(fileOut, info, tags); // 输出文件
                int packetSamples = type1Framerate * type1EncFrameLen / 1000;
                byte[] inBuf = new byte[ packetSamples * 2]; // 单声道
                byte[] data_packet = new byte[packetSamples * 2  * type1Channel];
                long granulePos = 0;

                while (fileIn.available() >= inBuf.length) {
                    int bytesRead = fileIn.read(inBuf, 0, inBuf.length);
                    if (bytesRead != inBuf.length) {
                        System.out.println("Warning: Incomplete frame read!");
                        break;
                    }
                    short[] pcm = BytesToShorts(inBuf, 0, bytesRead);
                    int bytesEncoded = encoder.encode(pcm, 0,  packetSamples, data_packet, 0, pcm.length);
                    if (bytesEncoded > 0) {
                        byte[] packet = new byte[bytesEncoded];
                        System.arraycopy(data_packet, 0, packet, 0, bytesEncoded);
                        OpusAudioData data = new OpusAudioData(packet);
                        granulePos += 48000 * type1EncFrameLen / 1000; // 每个包的样本数
                        data.setGranulePosition(granulePos);
                        file.writeAudioData(data);
                    } else {
                        System.out.println("Warning: Encoding failed!");
                    }
                }
                file.close();
                long end = System.currentTimeMillis();
                System.out.println("Time was " + (end - start) + "ms");
                System.out.println("Done!");
            }

//            fileIn.close();

        } catch (IOException | OpusException e) {
            log.error("处理错误: {}", e.getMessage());
        }
    }


    /*
    * 读物音频文件转byte数组
    * */
    public byte[] audioFileToByteArray(String filePath) throws IOException {
        File audioFile = new File(filePath);  // 创建File对象
        FileInputStream fis = new FileInputStream(audioFile); // 创建文件输入流
        byte[] audioBytes = new byte[(int) audioFile.length()]; // 根据文件长度创建字节数组

        // 将文件内容读取到字节数组中
        int bytesRead = fis.read(audioBytes);
        if (bytesRead != audioBytes.length) {
            throw new IOException("无法完全读取音频文件");
        }

        fis.close(); // 关闭输入流
        return audioBytes; // 返回字节数组
    }

}
