package com.easytranslate.config;

import com.sun.jna.platform.win32.Crypt32Util;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class ApiKeyStore {
  public enum StoredState { ABSENT, PRESENT, UNREADABLE }
  interface Protection {
    byte[] protect(byte[] plain);
    byte[] unprotect(byte[] encrypted);
  }
  private static final String KEY = "deepseekApiKey";
  private final Preferences preferences;
  private final Protection protection;

  public ApiKeyStore() { this(Preferences.userNodeForPackage(ApiKeyStore.class)); }
  public ApiKeyStore(Preferences preferences) {
    this(preferences, new Protection() {
      public byte[] protect(byte[] plain) { return Crypt32Util.cryptProtectData(plain); }
      public byte[] unprotect(byte[] encrypted) { return Crypt32Util.cryptUnprotectData(encrypted); }
    });
  }
  // Offline tests inject fake protection; the public production path always uses Windows DPAPI.
  ApiKeyStore(Preferences preferences, Protection protection) {
    this.preferences = Objects.requireNonNull(preferences);
    this.protection = Objects.requireNonNull(protection);
  }
  public boolean hasSavedKey() { return preferences.get(KEY, null) != null; }
  public StoredState storedState() {
    try { return load().isPresent() ? StoredState.PRESENT : StoredState.ABSENT; }
    catch (RuntimeException error) { return StoredState.UNREADABLE; }
  }

  public void save(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("API Key 不能为空");
    try {
      byte[] encrypted = protection.protect(apiKey.strip().getBytes(StandardCharsets.UTF_8));
      if (encrypted == null || encrypted.length == 0) throw new IllegalStateException("Empty protection result");
      persist(Base64.getEncoder().encodeToString(encrypted));
    } catch (RuntimeException error) { throw new IllegalStateException("无法保存 API Key，未确认持久化成功", error); }
  }

  public Optional<String> load() {
    try {
      String saved = preferences.get(KEY, null);
      if (saved == null) return Optional.empty();
      byte[] plain = protection.unprotect(Base64.getDecoder().decode(saved));
      String key = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plain)).toString();
      if (key.isBlank()) throw new IllegalStateException("Empty stored key");
      return Optional.of(key);
    } catch (RuntimeException | java.nio.charset.CharacterCodingException error) {
      throw new IllegalStateException("无法读取已保存的 API Key", error);
    }
  }

  public void delete() {
    try { persist(null); }
    catch (RuntimeException error) { throw new IllegalStateException("无法删除 API Key，未确认持久化成功", error); }
  }

  private void persist(String next) {
    String previous = preferences.get(KEY, null);
    try { write(next); preferences.flush(); }
    catch (RuntimeException | BackingStoreException error) {
      // Best effort only: a broken backend cannot guarantee rollback or deletion across restarts.
      try { write(previous); preferences.flush(); }
      catch (RuntimeException | BackingStoreException ignored) { }
      throw new IllegalStateException("无法确认密钥保存状态", error);
    }
  }
  private void write(String value) {
    if (value == null) preferences.remove(KEY); else preferences.put(KEY, value);
  }
}
