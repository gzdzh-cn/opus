package cn.gzdzh.opus.demos.web;

import lombok.extern.slf4j.Slf4j;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusInfo;
import org.gagravarr.opus.OpusTags;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.concentus.*;
import cn.hutool.json.JSONObject;

import static cn.gzdzh.opus.demos.util.Common.BytesToShorts;

@Slf4j
@RestController
public class ConvertController {

    private Integer type1Channel;
    private Integer type1Framerate;
    private Integer type1EncFrameLen;
    private Integer type1DecFrameLen;
    private String type1Name;

    @Autowired
    public ConvertController(Environment env) {
        // 从 Spring 的 Environment 中加载配置
        this.type1Channel = Integer.parseInt(env.getProperty("type1.channel", "1"));
        this.type1Framerate = Integer.parseInt(env.getProperty("type1.framerate", "16000"));
        this.type1EncFrameLen = Integer.parseInt(env.getProperty("type1.encFrameLen", "20"));
        this.type1DecFrameLen = Integer.parseInt(env.getProperty("type1.decFrameLen", "40"));
        this.type1Name = env.getProperty("type1.name", "defaultName");
    }

    public void loadSettings(String settingPath){
        if (!Files.exists(Paths.get(settingPath))) {
            log.warn("设置文件未找到");
            return;
        }
        try {
            List<String> settings = Files.readAllLines(Paths.get(settingPath));
            String[] data = settings.get(1).trim().replace(" ", "").split(",");
            type1Channel = Integer.parseInt(data[0]);
            type1Framerate = Integer.parseInt(data[1]);
            type1EncFrameLen = Integer.parseInt(data[2]);
            type1DecFrameLen = Integer.parseInt(data[3]);
            type1Name = data[4];
        } catch (IOException e) {
            throw  new RuntimeException("读取设置文件失败: " + e.getMessage());
        }
    }

    @PostMapping("/tranBinToWav")
    public ResponseEntity<?> tranBinToWav(@RequestBody JSONObject requestParams) {

        String inputFilePath = requestParams.getStr("inputFilePath");
        String outputPath = requestParams.getStr("outputPath");

        if (inputFilePath.isEmpty() || outputPath.isEmpty()) {
            return ResponseEntity.badRequest().body("请提供输入文件路径和输出路径");
        }
        // 读取设置文件
        loadSettings("opus_setting.txt");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));// 获取当前时间并格式化
        String outputFilename = outputPath + "/" + timestamp + "_" + type1Name + ".wav"; // 创建输出文件

        try {
            OpusDecoder decoder = new OpusDecoder(type1Framerate, type1Channel);// 初始化 Opus 解码器
            AudioFormat audioFormat = new AudioFormat(type1Framerate, 16, type1Channel, true, false);// 设置 WAV 文件参数
            ByteArrayOutputStream decodedOutput = new ByteArrayOutputStream();

            try (FileInputStream fileIn = new FileInputStream(inputFilePath)) {

                byte[] inBuf = new byte[type1EncFrameLen];
                long start = System.currentTimeMillis();
                int bytesRead;
                while ((bytesRead = fileIn.read(inBuf)) != -1) {
                    if (bytesRead < type1EncFrameLen) {
                        break;
                    }
                    byte[] pcm = new byte[type1DecFrameLen * 2];
                    int samplesDecoded = decoder.decode(inBuf, 0, bytesRead, pcm, 0, type1DecFrameLen, false);

                    if (samplesDecoded > 0) {
                        // 将解码的数据写入输出流
                        decodedOutput.write(pcm, 0, samplesDecoded* type1Channel * 2);
                    }
                }

                //  创建 WAV 文件
                try (AudioInputStream finalAudioInputStream = new AudioInputStream(
                        new ByteArrayInputStream(decodedOutput.toByteArray()),
                        audioFormat,
                        decodedOutput.size() / audioFormat.getFrameSize());
                     FileOutputStream fileOutputStream = new FileOutputStream(outputFilename)) {
                    AudioSystem.write(finalAudioInputStream, AudioFileFormat.Type.WAVE, fileOutputStream);
                }

                long end = System.currentTimeMillis();
                System.out.println("花费时间：" + (end - start) + "ms");
                System.out.println("处理完成: " + outputFilename);
            }
            // 创建响应Map
            Map<String, String> response = new HashMap<>();
            response.put("message", "处理完成");
            response.put("outputFilename", outputFilename);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("处理错误: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("处理错误: " + e.getMessage());
        }
    }

    @PostMapping("/tranWavToOpus")
    public ResponseEntity<?> tranWavToOpus(@RequestBody JSONObject requestParams) {

        String inputFilePath = requestParams.getStr("inputFilePath");
        String outputPath = requestParams.getStr("outputPath");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));// 获取当前时间并格式化
        String outputFilename = outputPath + "/" + timestamp + ".opus";// 创建输出 opus 文件
        try {
            // 读取设置文件
            loadSettings("opus_setting.txt");

            OpusEncoder encoder = new OpusEncoder(type1Framerate, type1Channel, OpusApplication.OPUS_APPLICATION_AUDIO);
            encoder.setBitrate(96000);
            encoder.setSignalType(OpusSignal.OPUS_SIGNAL_MUSIC);
            encoder.setComplexity(10);

            long start = System.currentTimeMillis();
            try (FileInputStream fileIn = new FileInputStream(inputFilePath);FileOutputStream fileOut = new FileOutputStream(outputFilename)){

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

            // 创建响应Map
            Map<String, String> response = new HashMap<>();
            response.put("message", "处理完成");
            response.put("outputFilename", outputFilename);
            return ResponseEntity.ok(response);

        } catch (IOException | OpusException e) {
            log.error("处理错误: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("处理错误: " + e.getMessage());
        }

    }

}
