# Chaquopy TTS Server

## 项目简介
Chaquopy TTS Server 是一个基于 Android 的文本转语音（TTS）服务应用，利用 Chaquopy 框架集成 Python 脚本，实现高效的语音合成功能。该应用支持多语言、多配置，并提供实时日志和内存监控功能，方便开发者调试和优化。

## 功能特性
- **文本转语音（TTS）**：
  - 支持多种语言和语音配置，用户可自定义语音参数（如语速、音调、音量等）。
  - 通过 Chaquopy 框架调用 Python 脚本，实现高效的语音合成。
  - 支持实时播放和暂停语音合成。

- **多配置管理**：
  - 允许用户添加、编辑和删除 TTS 配置，灵活调整语音合成效果。
  - 每个配置可独立设置语言、语速、音调等参数，满足不同场景需求。

- **实时日志**：
  - 内置日志收集功能，可实时查看应用运行状态。
  - 支持开始/停止日志收集，方便开发者调试和排查问题。
  - 日志界面自动滚动，确保最新日志始终可见。

- **内存监控**：
  - 实时监控应用内存使用情况，包括 Dalvik 堆、VmRSS 和 Native 堆内存。
  - 帮助开发者识别潜在的内存泄漏问题，优化应用性能。

- **用户友好界面**：
  - 采用 Material Design 风格，提供直观的操作体验。
  - 支持多语言界面，满足不同用户需求。

## 安装说明
1. 确保你的开发环境已安装 Android Studio 和 Chaquopy 插件。
2. 克隆项目到本地：
   ```bash
   git clone https://github.com/yourusername/chaquopy-tts.git
   ```
3. 在 Android Studio 中打开项目，等待 Gradle 同步完成。
4. 运行应用：
   - 连接 Android 设备或启动模拟器。
   - 点击"Run"按钮，选择目标设备，等待应用安装和启动。

## 使用方法
- **TTS 配置**：在"系统 TTS"界面，点击"+"按钮添加新配置，或点击现有配置进行编辑/删除。
- **日志查看**：在"日志"界面，点击右上角的播放/暂停按钮控制日志收集，实时查看应用运行日志。
- **内存监控**：在"日志"界面顶部，查看实时内存使用情况，包括 Dalvik 堆、VmRSS 和 Native 堆内存。

## 项目结构
```
app/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/hihi/ttsserver/
│   │   │       ├── ui/
│   │   │       │   ├── LogFragment.kt
│   │   │       │   ├── SysTtsFragment.kt
│   │   │       │   └── SettingsFragment.kt
│   │   │       ├── service/
│   │   │       │   └── TtsService.kt
│   │   │       └── utils/
│   │   │           ├── LogCollector.kt
│   │   │           └── MemoryMonitor.kt
│   │   ├── res/
│   │   │   ├── drawable/
│   │   │   │   ├── ic_play_24px.xml
│   │   │   │   ├── ic_pause_24px.xml
│   │   │   │   └── ...
│   │   │   ├── layout/
│   │   │   │   ├── fragment_log.xml
│   │   │   │   ├── fragment_systts.xml
│   │   │   │   └── ...
│   │   │   └── ...
│   │   └── ...
│   └── ...
└── ...
```

## 贡献指南
欢迎贡献代码或提出建议！请遵循以下步骤：
1. Fork 项目并克隆到本地。
2. 创建新的分支：`git checkout -b feature/your-feature-name`。
3. 提交更改：`git commit -m "Add your feature"`。
4. 推送到远程仓库：`git push origin feature/your-feature-name`。
5. 提交 Pull Request，等待审核。

## 许可证
本项目采用 MIT 许可证。详情请查看 [LICENSE](LICENSE) 文件。

---

如有问题或建议，请提交 Issue 或联系项目维护者。
