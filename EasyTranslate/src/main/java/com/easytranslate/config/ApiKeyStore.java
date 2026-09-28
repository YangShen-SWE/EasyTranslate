package com.easytranslate.config;

import com.sun.jna.platform.win32.Crypt32Util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;

public final class ApiKeyStore {
  private static final String KEY = "deepseekApiKey";
  private final Preferences preferences;

  public ApiKeyStore() {
    this(Preferences.userNodeForPackage(ApiKeyStore.class));
  }

  public ApiKeyStore(Preferences preferences) {
    this.preferences = Objects.requireNonNull(preferences);
  }

  public boolean hasSavedKey() {
    return preferences.get(KEY, null) != null;
  }

  public void save(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API Key 不能为空");
    }

    byte[] plain = apiKey.strip().getBytes(StandardCharsets.UTF_8);
    byte[] protectedBytes = Crypt32Util.cryptProtectData(plain);
    preferences.put(KEY, Base64.getEncoder().encodeToString(protectedBytes));
  }

  public Optional<String> load() {
    String saved = preferences.get(KEY, null);
    if (saved == null) {
      return Optional.empty();
    }

    try {
      byte[] protectedBytes = Base64.getDecoder().decode(saved);
      byte[] plain = Crypt32Util.cryptUnprotectData(protectedBytes);
      return Optional.of(new String(plain, StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      throw new IllegalStateException("无法读取已保存的 API Key", e);
    }
  }

  public void delete() {
    preferences.remove(KEY);
  }
}
