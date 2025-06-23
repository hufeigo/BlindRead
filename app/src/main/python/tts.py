import asyncio
import edge_tts
import io
import os
import tempfile
import json
import re
import sys
from typing import Optional, List, Dict, Tuple, Any
from dataclasses import dataclass
from edge_tts import VoicesManager

@dataclass
class VoiceStyle:
    name: str
    voice: str
    scope: str = ""
    style: Optional[str] = None
    role: Optional[str] = None
    rate: float = 0.0
    pitch: float = 0.0
    volume: float = 0.0
    style_degree: float = 1.0

@dataclass
class TextSegment:
    text: str
    style: VoiceStyle
    start_time: float = 0.0
    end_time: float = 0.0

class TextParser:
    def __init__(self):
        self.rules = []
        self.default_style = VoiceStyle(
            name="default",
            voice="zh-CN-XiaoxiaoNeural",
            style="novel"
        )
    
    def add_rule(self, pattern: str, style: VoiceStyle):
        """添加文本匹配规则"""
        self.rules.append((re.compile(pattern), style))
    
    def parse(self, text: str) -> List[TextSegment]:
        """解析文本，返回带样式的文本段列表"""
        segments = []
        last_end = 0
        
        # 遍历所有规则
        for pattern, style in self.rules:
            # 使用 finditer 找出所有匹配
            for match in pattern.finditer(text):
                # 添加匹配前的旁白
                if match.start() > last_end:
                    segments.append(TextSegment(
                        text=text[last_end:match.start()],
                        style=self.default_style
                    ))
                
                # 添加匹配内容
                segments.append(TextSegment(
                    text=match.group(1),
                    style=style
                ))
                last_end = match.end()
        
        # 添加最后一段旁白（如果有）
        if last_end < len(text):
            segments.append(TextSegment(
                text=text[last_end:],
                style=self.default_style
            ))
        
        return segments

class TTSEngine:
    def __init__(self):
        self._config_cache = None
        self._voices_cache = None
        self.text_parser = TextParser()
        # 初始化默认配置
        self._config_cache = {
            "default_voice_style": {
                "name": "default",
                "voice": "zh-CN-XiaoxiaoNeural",
                "style": "novel",
                "rate": 0.0,
                "pitch": 0.0,
                "volume": 0.0,
                "style_degree": 1.0
            },
            "rules": []
        }
        print("【TTS初始化】✓ 引擎初始化完成")
    
    @property
    def config(self) -> Dict:
        """获取配置（使用缓存）"""
        return self._config_cache
    
    def set_config_from_dict(self, config_list: List[Dict]):
        """从字典列表设置配置（包括默认语音风格和解析规则），并按优先级处理"""
        # 清空现有规则
        self.text_parser.rules = []

        default_style_config = None # 存储"仅旁白"或"朗读全部"的有效配置
        dialogue_rules_configs = [] # 存储"仅对话"的有效配置

        # 步骤1: 遍历并收集所有启用的配置项
        enabled_configs = [item for item in config_list if item.get("enabled", True)]

        # 步骤2: 确定 default_style （"仅旁白"优先级高于"朗读全部"）
        # 尝试查找"仅旁白"的配置
        scope="",
        for item in enabled_configs:
            if item.get("scope") == "仅旁白":
                default_style_config = item
                scope="仅旁白"
                print(f"【TTS配置】使用 '仅旁白' 配置: {item.get('name')}")
                break # 找到即停止，后续的"朗读全部"将不再生效
        
        # 如果没有"仅旁白"的配置，尝试查找"朗读全部"的配置
        if default_style_config is None:
            for item in enabled_configs:
                if item.get("scope") == "朗读全部":
                    default_style_config = item
                    scope="朗读全部"
                    print(f"【TTS配置】使用 '朗读全部' 配置: {item.get('name')}")
                    break # 找到即停止

        # 设置 text_parser.default_style
        if default_style_config:
            voice_data = default_style_config.get("voice", {})
            self.text_parser.default_style = VoiceStyle(
                name=default_style_config.get("name", "default"),
                scope=scope,
                voice=voice_data.get("name", "zh-CN-XiaoxiaoNeural"),
                style=voice_data.get("style"),
                role=voice_data.get("role"),
                rate=default_style_config.get("rate", 0.0),
                pitch=default_style_config.get("pitch", 0.0),
                volume=default_style_config.get("volume", 0.0),
                style_degree=default_style_config.get("styleIntensity", 100.0) / 100.0
            )
        else:
            print("【TTS配置】使用默认语音风格")

        # 步骤3: 收集"仅对话"规则
        for item in enabled_configs:
            if item.get("scope") == "仅对话":
                dialogue_rules_configs.append(item)

        # 步骤4: 添加"仅对话"规则到 TextParser
        dialogue_pattern = re.compile(r'[\'"“”'']([^\'"“”'':]*?)[\'""“”'']')
        for rule_item in dialogue_rules_configs:
            voice_data = rule_item.get("voice", {})
            current_voice_style = VoiceStyle(
                name=rule_item.get("name", "dialogue_rule"),
                voice=voice_data.get("name", "zh-CN-XiaoxiaoNeural"),
                scope="仅对话",
                style=voice_data.get("style"),
                role=voice_data.get("role"),
                rate=rule_item.get("rate", 0.0),
                pitch=rule_item.get("pitch", 0.0),
                volume=rule_item.get("volume", 0.0),
                style_degree=rule_item.get("styleIntensity", 100.0) / 100.0
            )
            self.text_parser.add_rule(dialogue_pattern, current_voice_style)
            print(f"【TTS配置】添加对话规则: {rule_item.get('name')}")

    async def _get_voices(self) -> VoicesManager:
        """获取语音列表（使用缓存）"""
        if self._voices_cache is None:
            self._voices_cache = await VoicesManager.create()
        return self._voices_cache
    
    async def _synthesize_segment_async(self, segment: TextSegment) -> Optional[bytes]:
        """合成单个文本段"""
        try:
            # 创建临时文件
            temp_suffix = ".mp3"
            temp_file = tempfile.NamedTemporaryFile(delete=False, suffix=temp_suffix).name
            
            try:
                print(f"【合成】: rate={segment.style.rate} volume={segment.style.volume} {segment.text}")
                communicate = edge_tts.Communicate(
                    text=segment.text,
                    voice=segment.style.voice,
                    rate=f"{int(segment.style.rate):+d}%",
                    pitch=f"{int(segment.style.pitch):+d}Hz",
                    volume=f"{int(segment.style.volume):+d}%"
                )
                
                await communicate.save(temp_file)
                
                with open(temp_file, 'rb') as f:
                    audio_data = f.read()
                    return audio_data
                    
            finally:
                try:
                    os.remove(temp_file)
                except:
                    pass
                
        except Exception as e:
            print(f"【合成】错误: {str(e)}")
            return None
    
    async def _synthesize_async(self, text: str) -> Optional[bytes]:
        """异步合成完整文本"""
        try:
            # 解析文本
            segments = self.text_parser.parse(text)
            
            # 合成每个段落
            audio_segments = []
            for segment in segments:
                print(f"【合成】{segment.style.name}: {segment.text}")
                audio_data = await self._synthesize_segment_async(segment)
                if audio_data:
                    audio_segments.append(audio_data)
            
            if not audio_segments:
                return None
            
            # 合并所有音频段
            combined_audio = audio_segments[0]
            for segment in audio_segments[1:]:
                combined_audio += segment
            
            return combined_audio
            
        except Exception as e:
            print(f"【合成】错误: {str(e)}")
            return None
    
    def synthesize(self, text: str) -> Optional[bytes]:
        """同步合成接口"""
        try:
            print(f"【TTS请求】文本: {text}")
            
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            audio_data = loop.run_until_complete(self._synthesize_async(text))
            loop.close()
            
            if audio_data is None:
                print("【TTS请求】失败：未获取到音频数据")
            else:
                print(f"【TTS请求】成功：{len(audio_data)} 字节")
            
            return audio_data
            
        except Exception as e:
            print(f"【TTS请求】错误: {str(e)}")
            return None

# 创建全局TTS引擎实例
_tts_engine = TTSEngine()

def synthesize(text: str) -> Optional[bytes]:
    """对外暴露的合成接口"""
    return _tts_engine.synthesize(text)

def update_config(json_config_string: str):
    """从Java接收JSON字符串，更新TTS引擎配置"""
    try:
        config_list = json.loads(json_config_string)
        if isinstance(config_list, list):
            _tts_engine.set_config_from_dict(config_list)
        else:
            print("【TTS配置】错误: 配置格式不正确")
    except json.JSONDecodeError as e:
        print(f"【TTS配置】错误: JSON格式无效: {e}")
    except Exception as e:
        print(f"【TTS配置】错误: {e}")

if __name__ == "__main__":
    print("【TTS】启动测试")
    
    # 示例文本
    text = '这是一段旁白。（这是一段插入的旁白。）"这是一段对话！"这是另一段旁白。'

    # 外部传递语音风格配置的例子
    json_config = [
        {
            "apiName": "微软Edge",
            "audioFormat": "audio-16khz-32kbitrate-mono-mp3",
            "enabled": True,
            "id": 11,
            "name": "晓伊 (女声, 卡通/小说, 活泼)",
            "pitch": -1.0,
            "rate": 0.0,
            "scope": "朗读全部",
            "styleIntensity": 70.0,
            "text": "单击右侧按钮可测试并播放",
            "voice": {
                "name": "zh-CN-XiaoyiNeural",
                "role": "Lively",
                "style": "Cartoon"
            },
            "volume": 1.0
        },
        {
            "apiName": "微软Edge",
            "audioFormat": "audio-16khz-32kbitrate-mono-mp3",
            "enabled": True,
            "id": 12,
            "name": "晓晓 (女声, 新闻/小说, 温暖)",
            "pitch": 0.0,
            "rate": 0.0,
            "scope": "仅对话",
            "styleIntensity": 50.0,
            "text": "点击右侧按钮测试朗读效果",
            "voice": {
                "name": "zh-CN-XiaoxiaoNeural",
                "role": "Warm",
                "style": "News"
            },
            "volume": 0.0
        },
        {
            "apiName": "微软Edge",
            "audioFormat": "audio-16khz-32kbitrate-mono-mp3",
            "enabled": True,
            "id": 13,
            "name": "YunJ(女声, 新闻/小说, 温暖))",
            "pitch": 0.0,
            "rate": 0.0,
            "scope": "仅旁白",
            "styleIntensity": 100.0,
            "text": "默认朗读全部的风格",
            "voice": {
                "name": "zh-TW-YunJheNeural"
            },
            "volume": 0.0
        }
    ]

    print("【TTS】加载配置")
    update_config(json.dumps(json_config))

    print("【TTS】开始合成")
    text_for_test = '找不见无舌，屋顶的老庄对云烨说:“侯爷，无舌追出去了，还拿走了一张强弩。”'
    
    segments = _tts_engine.text_parser.parse(text_for_test)
    for segment in segments:
        print(f"{segment.style.name}: {segment.text}")

    if ( 1==2 ):
        sys.exit(0)

    audio = synthesize(text_for_test)
    


