package com.litongjava.kit.service;

import java.util.ArrayList;
import java.util.List;

import com.litongjava.chat.UniChatClient;
import com.litongjava.chat.UniChatMessage;
import com.litongjava.chat.UniChatRequest;
import com.litongjava.chat.UniChatResponse;
import com.litongjava.consts.ModelPlatformName;
import com.litongjava.gemini.GoogleModels;
import com.litongjava.openai.chat.ChatResponseMessage;

public class GoogleGeminiSearchService {

  public ChatResponseMessage search(String question) {
    List<UniChatMessage> messages = new ArrayList<>();
    messages.add(UniChatMessage.buildUser(question));
    UniChatRequest uniChatRequest = new UniChatRequest(ModelPlatformName.GOOGLE, GoogleModels.GEMINI_2_5_PRO, messages);
    uniChatRequest.setEnable_search(true);

    UniChatResponse response = UniChatClient.generate(uniChatRequest);
    ChatResponseMessage message = response.getMessage();
    message.setRole(null);
    return message;
  }
}
