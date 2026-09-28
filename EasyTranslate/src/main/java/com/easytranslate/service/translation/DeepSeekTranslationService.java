package com.easytranslate.service.translation;

import com.easytranslate.model.Translation;
import com.easytranslate.config.ApiKeyStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DeepSeekTranslationService implements TranslationService
{
  private static final URI ENDPOINT = URI.create("https://api.deepseek.com/chat/completions");
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(
      Duration.ofSeconds(5)).build();
  private final ObjectMapper mapper = new ObjectMapper();
  private final ApiKeyStore apiKeyStore;

  public DeepSeekTranslationService(ApiKeyStore apiKeyStore)
  {
    this.apiKeyStore = Objects.requireNonNull(apiKeyStore);
  }
  @Override
  public Translation translate(String sourceText){
    if(sourceText == null || sourceText.isBlank()){
      throw new IllegalArgumentException("待翻译文本不能为空");
    }
    String apiKey = apiKeyStore.load().orElseThrow(() ->
        new IllegalStateException("请先在设置中保存 DeepSeek API Key"));
    try{
      ObjectNode body = mapper.createObjectNode();
      //使用deepseek-flash模型
      body.put("model","deepseek-flash");
      //使用非思考模式，对于简单翻译来说明显会省token
      body.putObject("thinking").put("type","disabled");
      body.put("stream",false);
      var message = body.putArray("messages");
      message.addObject().put("role","system").put("content","将用户给出的英文翻译成简洁、准确的中文。只返回译文。");
      message.addObject().put("role","user").put("content",sourceText);

      HttpRequest request = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(20))
          .header("Authorization","Bearer " + apiKey)
          .header("Content-Type","application/json")
          .POST(HttpRequest.BodyPublishers.ofString(
              mapper.writeValueAsString(body), StandardCharsets.UTF_8)).build();

      HttpResponse<String> response = client.send(
          request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      if(response.statusCode() != 200){
        throw new IllegalStateException("翻译接口返回 http" + response.statusCode());
      }

      JsonNode json = mapper.readTree(response.body());
      String translatedText = json.path("choices").path(0).path("message").path("content").asText().strip();

      if(translatedText.isBlank()){
        throw new IllegalStateException("翻译没有返回译文");
      }
      return new Translation(sourceText,translatedText);
    }catch (InterruptedException e){
      Thread.currentThread().interrupt();
      throw new IllegalStateException("翻译请求被中断",e);
    }catch (IOException e){
      throw new IllegalStateException("翻译请求或响应处理失败",e);
    }
  }
}
