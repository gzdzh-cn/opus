# opus转换
spring 2.6.13
jdk 1.8


> opus转换常用格式音频

### 更新日志
v1.0.0 -日期：2024-11-18
- 二进制文件转换为 WAV 格式
- WAV 转换为 opus 格式

## 运行
src/main/java/cn/gzdzh/opus/TanApplication.java

## 接口文档

### URL

`POST http://127.0.0.1:8080/tranBinToWav`
> 此接口用于将输入的二进制文件转换为 WAV 格式。
#### 请求

##### 请求头

- `Content-Type: application/json`

##### 请求体参数

- `inputFilePath` (字符串): 需要转换的 `.bin` 输入文件的路径。
- `outputPath` (字符串): 保存输出 WAV 文件的目录。

###### 示例

```json
{
  "inputFilePath":"/Volumes/disk/site/java/demo/tanwav/input.bin",
  "outputPath":"/Volumes/disk/site/java/demo/tanwav"
}
```

#### 响应
##### 成功 (200 OK)
* message (字符串): 表示处理成功的确认信息。
* outputFilename (字符串): 生成的 WAV 文件的路径。

###### 示例
```shell
{
    "message": "处理完成",
    "outputFilename": "/Volumes/disk/site/java/demo/tanwav/20241118154209784_opus_dec.wav"
}

```



### URL

`POST http://127.0.0.1:8080/tranWavToOpus`
> 此接口用于将输入 WAV 转换为 opus 格式。
#### 请求

##### 请求头

- `Content-Type: application/json`

##### 请求体参数

- `inputFilePath` (字符串): 需要转换的 `.wav` 输入文件的路径。
- `outputPath` (字符串): 保存输出 opus 文件的目录。

###### 示例

```json
{
  "inputFilePath":"/Volumes/disk/site/java/demo/tanwav/input.wav",
  "outputPath":"/Volumes/disk/site/java/demo/tanwav"
}
```

#### 响应
##### 成功 (200 OK)
* message (字符串): 表示处理成功的确认信息。
* outputFilename (字符串): 生成的 WAV 文件的路径。

###### 示例
```shell
{
    "message": "处理完成",
    "outputFilename": "/Volumes/disk/site/java/demo/tanwav/20241118155536920.opus"
}

```