package com.litongjava.uni.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.litongjava.fishaudio.tts.FishAudioClient;
import com.litongjava.fishaudio.tts.FishAudioTTSRequestVo;
import com.litongjava.minimax.MiniMaxHttpClient;
import com.litongjava.minimax.MiniMaxTTSResponse;
import com.litongjava.minimax.MiniMaxVoice;
import com.litongjava.model.http.response.ResponseVo;
import com.litongjava.openai.tts.OpenAiTTSClient;
import com.litongjava.tio.utils.crypto.Md5Utils;
import com.litongjava.tio.utils.hex.HexUtils;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.lang.ChineseUtils;
import com.litongjava.volcengine.VolceTtsClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ManimTTSService {

  private static final String CACHE_DIR = "cache";

  public byte[] tts(String input, String provider, String voiceId) {
    // 1. Determine defaults based on whether text contains Chinese
    if (ChineseUtils.containsChinese(input)) {
      provider = StrUtil.defaultIfBlank(provider, "minimax");
      voiceId  = StrUtil.defaultIfBlank(voiceId, MiniMaxVoice.Chinese_Mandarin_Gentleman);
    } else {
      provider = StrUtil.defaultIfBlank(provider, "minimax");
      voiceId  = StrUtil.defaultIfBlank(voiceId, "English_magnetic_voiced_man");
    }
    log.info("TTS request: input='{}', provider='{}', voiceId='{}'", input, provider, voiceId);

    // 2. Build a file‐based cache key: md5_provider_voice.mp3
    String md5 = Md5Utils.md5Hex(input);
    String fileName = md5 + "_" + provider + "_" + voiceId + ".mp3";
    Path cachePath = Paths.get(CACHE_DIR, fileName);

    // 3. Try reading from cache
    if (Files.exists(cachePath)) {
      log.info("Cache hit: reading TTS from {}", cachePath);
      try {
        return Files.readAllBytes(cachePath);
      } catch (IOException e) {
        log.error("Failed to read cache file '{}', will regenerate", cachePath, e);
        // remove corrupted cache file
        try { Files.deleteIfExists(cachePath); } catch (IOException ignored) {}
      }
    }

    // 4. Cache miss or read‐error → generate new audio
    byte[] audioBytes;
    switch (provider.toLowerCase()) {
      case "volce":
        audioBytes = VolceTtsClient.tts(input);
        break;

      case "fishaudio":
        FishAudioTTSRequestVo vo = new FishAudioTTSRequestVo();
        vo.setText(input);
        vo.setReference_id(voiceId);
        ResponseVo fishResp = FishAudioClient.speech(vo);
        if (fishResp.isOk()) {
          audioBytes = fishResp.getBodyBytes();
        } else {
          log.error("FishAudio TTS error: {}", fishResp.getBodyString());
          return FileUtil.readBytes(new File("default.mp3"));
        }
        break;

      case "minimax":
        MiniMaxTTSResponse minimaxResp = MiniMaxHttpClient.speech(input, voiceId);
        String audioHex = minimaxResp.getData().getAudio();
        audioBytes = HexUtils.decodeHex(audioHex);
        break;

      default:  // fallback to OpenAI
        ResponseVo openAiResp = OpenAiTTSClient.speech(input);
        if (openAiResp.isOk()) {
          audioBytes = openAiResp.getBodyBytes();
        } else {
          log.error("OpenAI TTS error: {}", openAiResp.getBodyString());
          return FileUtil.readBytes(new File("default.mp3"));
        }
        break;
    }

    // 5. Save to cache directory
    try {
      Files.createDirectories(cachePath.getParent());
      Files.write(cachePath, audioBytes);
      log.info("Generated new TTS audio and cached at {}", cachePath);
    } catch (IOException e) {
      log.error("Failed to write cache file '{}'", cachePath, e);
      // Not fatal: still return generated audio
    }

    return audioBytes;
  }
}
