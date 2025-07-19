import re
import asyncio
import io
from typing import Optional, List, Dict, Any
import edge_tts
import time
import json


# =====================
# 数据结构定义
# =====================
class TextSegment:
    def __init__(self, text: str, voice: dict, role: Optional[str] = None):
        self.text = text
        self.voice = voice
        self.role = role
    def __repr__(self):
        return f"TextSegment(text={self.text!r}, voice={self.voice.get('name')!r}, role={self.role!r})"

# =====================
# 配置与全局变量
# =====================
_json_config: List[Dict[str, Any]] = []
narrator_voice: List[dict] = []
dialogue_voice: List[dict] = []
all_voice: List[dict] = []
# 添加最大内存限制 (50MB)
_max_memory_size = 50 * 1024 * 1024
# 全局voice循环索引
narrator_idx_global = 0
dialogue_idx_global = 0
all_idx_global = 0

# =====================
# 配置解析与分配
# =====================
def set_voice_config(json_config):
    global _json_config, narrator_voice, dialogue_voice, all_voice, _last_config_hash
    print(f"{json_config}")
    # 兼容从 Java/Kotlin 端传入的 JSON 字符串
    if isinstance(json_config, str):
        json_config = json.loads(json_config)
    _json_config = json_config
    narrator_voice, dialogue_voice, all_voice = parse_voice_config(json_config)

def parse_voice_config(json_config: List[Dict[str, Any]]):
    narrator_voice = []
    dialogue_voice = []
    all_voice = []
    for item in json_config:
        scope = item.get("scope", "")
        if "旁白" in scope:
            narrator_voice.append(item)
        elif "对话" in scope:
            dialogue_voice.append(item)
        elif "全部" in scope:
            all_voice.append(item)
    return narrator_voice, dialogue_voice, all_voice

# =====================
# 文本解析与分配
# =====================
def parse_dialog_narrator_with_role(text: str):
    """将文本分割为旁白和对话，并识别对话角色"""
    pattern = r'[“”"\'‘’]'
    parts = re.split(pattern, text)
    segments = []
    for idx, part in enumerate(parts):
        if not part.strip():
            continue
        if idx % 2 == 1:
            # 对话，尝试识别前一个旁白的角色
            role = None
            if segments:
                prev_type, prev_content, *_ = segments[-1]
                if prev_type == 'narrator':
                    m = re.search(
                        r'([\w\u4e00-\u9fa5]+?)(?:又)?(说|问|答|道|喊|叫|讲|笑|哭|叹|问道|说道|答道|叫道|喊道|笑道|哭道|讲道|叹道)[，,。.!?…]*$',
                        prev_content)
                    if m:
                        role = m.group(1)
            segments.append(('dialog', part.strip(), role))
        else:
            segments.append(('narrator', part.strip(), None))
    return segments

def assign_voice_to_segments(
    segments, narrator_voice, dialogue_voice, all_voice,
    narrator_idx=0, dialogue_idx=0, all_idx=0
):
    """为每个文本段分配voice，支持循环分配"""
    narrator_len = len(narrator_voice)
    dialogue_len = len(dialogue_voice)
    all_len = len(all_voice)
    result = []
    for seg_type, content, role in segments:
        if seg_type == 'narrator':
            if narrator_len > 0:
                voice = narrator_voice[narrator_idx % narrator_len]
                narrator_idx += 1
            elif all_len > 0:
                voice = all_voice[all_idx % all_len]
                all_idx += 1
            else:
                voice = {}
            result.append(TextSegment(content, voice, role=None))
        elif seg_type == 'dialog':
            if dialogue_len > 0:
                voice = dialogue_voice[dialogue_idx % dialogue_len]
                dialogue_idx += 1
            elif all_len > 0:
                voice = all_voice[all_idx % all_len]
                all_idx += 1
            else:
                voice = {}
            result.append(TextSegment(content, voice, role=role))
    return result, narrator_idx, dialogue_idx, all_idx

# =====================
# 合成相关
# =====================
async def synthesize_segment_async(segment: TextSegment):
    """合成单个文本段（参考tts.py实现）"""
    try:
        import edge_tts  # 延迟导入，避免未安装时报错
        print(f"【合成】{segment.voice.get('name')}: {segment.text} (角色: {segment.role})")
        communicate = edge_tts.Communicate(
            text=segment.text,
            voice=segment.voice.get('voice', '').get('name', ''),
            rate=f"{int(segment.voice.get('rate', 0)):+d}%",
            pitch=f"{int(segment.voice.get('pitch', 0)):+d}Hz",
            volume=f"{int(segment.voice.get('volume', 0)):+d}%"
        )
        audio_stream = io.BytesIO()
        try:
            async def collect_audio():
                async for chunk in communicate.stream():
                    if chunk.get("type") == "audio" and "data" in chunk:
                        audio_stream.write(chunk["data"])
            await asyncio.wait_for(collect_audio(), timeout=30)
            return audio_stream.getvalue()
        except asyncio.TimeoutError:
            print(f"【合成】超时: {segment.text}")
            return None
    except Exception as e:
        print(f"【合成】错误: {str(e)}")
        return None

# 添加最大内存限制 (50MB)
_max_memory_size = 50 * 1024 * 1024
async def synthesize_segments(segments: List[TextSegment]):
    """批量合成所有TextSegment，返回合成后的数据流，不落盘"""
    audio_segments = []
    total_size = 0
    for i, segment in enumerate(segments, 1):
        audio = await synthesize_segment_async(segment)
        if audio:
            total_size += len(audio)
            if total_size > _max_memory_size:
                print(f"【合成】内存使用超限，停止合成: {total_size} bytes")
                break
            audio_segments.append(audio)
    if not audio_segments:
        return None
    if len(audio_segments) == 1:
        return audio_segments[0]
    combined_stream = io.BytesIO()
    for segment in audio_segments:
        combined_stream.write(segment)
    return combined_stream.getvalue()

# =====================
# 对外主流程API
# =====================
def synthesize_text(text: str) -> Optional[bytes]:
    """对外API：传入文本，自动分段分配voice并合成音频，返回音频数据流"""
    global narrator_idx_global, dialogue_idx_global, all_idx_global
    print(f"[日志] 接收到文本: {text}")
    start_time = time.time()
    parsed = parse_dialog_narrator_with_role(text)
    segments, narrator_idx_global, dialogue_idx_global, all_idx_global = assign_voice_to_segments(
        parsed, narrator_voice, dialogue_voice, all_voice,
        narrator_idx_global, dialogue_idx_global, all_idx_global
    )
    result = asyncio.run(synthesize_segments(segments))
    elapsed = time.time() - start_time
    print(f"[日志] 合成耗时: {elapsed:.2f} 秒, 循环全部 {all_idx_global}, 旁白 {narrator_idx_global}, 对话 {dialogue_idx_global}")
    return result

# =====================
# 示例与测试
# =====================
if __name__ == '__main__':
    # 示例voice配置
    json_config = """
    [{"apiName":"微软Edge","audioFormat":"audio-16khz-32kbitrate-mono-mp3","enabled":true,"id":0,"name":"晓晓 (女声, 新闻/小说, 温暖)","pitch":0.0,"rate":0.0,"scope":"朗读全部","styleIntensity":50.0,"text":"单击右侧按钮可测试并播放","voice":{"name":"zh-CN-XiaoxiaoNeural","role":"Warm","style":"News"},"volume":0.0},
    {"apiName":"微软Edge","audioFormat":"audio-16khz-32kbitrate-mono-mp3","enabled":true,"id":1,"name":"云夏 (男声, 卡通/小说, 可爱)","pitch":0.0,"rate":20.0,"scope":"仅对话","styleIntensity":50.0,"text":"单击右侧按钮可测试并播放","voice":{"name":"zh-CN-YunxiaNeural","role":"Cute","style":"Cartoon"},"volume":0.0},{"apiName":"微软Edge","audioFormat":"audio-16khz-32kbitrate-mono-mp3","enabled":true,"id":2,"name":"晓晓 (女声, 新闻/小说, 温暖)","pitch":0.0,"rate":20.0,"scope":"仅旁白","styleIntensity":50.0,"text":"单击右侧按钮可测试并播放","voice":{"name":"zh-CN-XiaoxiaoNeural","role":"Warm","style":"Novel"},"volume":0.0}]
    """

    set_voice_config(json_config)

    # 测试用例
    test_cases = [
        '“等一下，”他说，“还等一下，”他又说，“我来了”',
        # '找不见无舌，屋顶的老庄对云烨说:"侯爷，无舌追出去了，还拿走了一张强弩。"然后城',
        # '“陛下，这样做是不是嚣张了些？”云烨的旁边是也看得发傻的皇帝。',
        # '云烨说，“陛下，这样做是不是嚣张了些？”'
    ]
    for idx, txt in enumerate(test_cases, 1):
        audio_data = synthesize_text(txt)
        if audio_data:
            with open(f"output_test_{idx}.mp3", "wb") as f:
                f.write(audio_data)
            print(f"【合成】音频已保存到: output_test_{idx}.mp3")
