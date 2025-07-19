# BlindRead-main

## 项目简介

BlindRead-main 是一个基于 Android + Python 的听书/文本转语音（TTS）应用。
它通过 Chaquopy 框架将 Python 的 edge-tts 能力集成到 Android 端，实现了灵活的文本分段、角色识别、多 voice 配置循环、合成音频等功能。
适合小说朗读、辅助阅读等场景。

---

## 主要功能

- **Android 系统级 TTS 服务**：可作为系统 TTS 引擎，支持第三方阅读器调用。
- **多角色/旁白自动分段**：支持中英文引号分段，自动识别旁白、对话及角色。
- **多 voice 配置循环分配**：可为不同角色/旁白分配不同语音，支持循环使用。
- **自定义语速/音调/音量**：每个 voice 可独立配置 rate、pitch、volume。
- **实时日志与内存监控**：便于调试和性能优化。
- **配置管理**：支持多套 TTS 配置切换，界面友好。

---

## 项目结构

```
BlindRead-main/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/hihi/ttsserver/   # Android 端主代码
│   │   │   ├── python/                    # Python TTS 脚本（edge-tts 逻辑）
│   │   │   ├── res/                       # 资源文件
│   │   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── requirements.txt
├── build.gradle
└── README.md
```

---

## 安装与编译

### 1. Android 端（命令行编译/打包/安装）

在项目根目录下，使用 gradlew 命令：

```bash
# 编译 Debug 包
./gradlew assembleDebug

# 编译 Release 包
./gradlew assembleRelease

# 安装 Debug 包到已连接设备
./gradlew installDebug

# 安装 Release 包到已连接设备
./gradlew installRelease
```

如需清理构建缓存：

```bash
./gradlew clean
```

### 2. Android 端（Android Studio）

- 使用 Android Studio 打开 `BlindRead-main` 项目
- 需联网自动下载 edge-tts 依赖（见 `app/build.gradle`）
- 直接编译运行到设备或模拟器

### 3. Python 端（独立测试/调试）

```bash
cd app/src/main/python
pip install edge-tts
python test.py
```

---

## 外部调用（Python TTS 脚本）

可通过 `set_voice_config` 和 `synthesize_text` 进行自定义合成：

```python
from test import set_voice_config, synthesize_text

set_voice_config(your_voice_config)
audio_data = synthesize_text("你的文本内容")
with open("output.mp3", "wb") as f:
    f.write(audio_data)
```

---

## 依赖导出与打包

```bash
pip freeze > requirements.txt
pip install pyinstaller
pyinstaller --onefile test.py
```

---

## 常见问题

- **edge-tts 未安装/网络异常**：请确保 Python 端已安装 edge-tts，且设备可联网。
- **Android 端 Python 初始化失败**：请检查 Chaquopy 配置和 Python 依赖。
- **TTS 配置无效**：请在设置界面正确配置并保存 TTS 语音参数。

---

## 贡献与反馈

欢迎提交 issue、PR 或建议！

---

如有问题请提交 issue 或联系作者。
