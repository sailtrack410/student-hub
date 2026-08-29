package com.studenthub.modules.aichat.service;

/**
 * 语音识别：音频流转写为文本。使用运行时快照中的 ASR 默认模型（如 Qwen3-ASR-1.7B）。
 */
public interface AiSpeechService {

    /**
     * 将音频字节转写为中文文本。
     *
     * @param audio       音频内容（mp3/aac/wav 等）
     * @param filename    原始文件名（用于推断扩展名）
     * @param contentType 音频 MIME，如 audio/mpeg
     */
    String transcribe(byte[] audio, String filename, String contentType);
}