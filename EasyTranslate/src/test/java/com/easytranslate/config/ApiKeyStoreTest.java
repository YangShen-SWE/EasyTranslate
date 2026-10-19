package com.easytranslate.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ApiKeyStoreTest {
  private static final String KEY = "deepseekApiKey";
  private static final ApiKeyStore.Protection FAKE = new ApiKeyStore.Protection() {
    public byte[] protect(byte[] bytes) { return transform(bytes); }
    public byte[] unprotect(byte[] bytes) { return transform(bytes); }
    private byte[] transform(byte[] bytes) { byte[] copy = Arrays.copyOf(bytes, bytes.length); for (int i = 0; i < copy.length; i++) copy[i] ^= 0x5A; return copy; }
  };
  private ApiKeyStore store(MemoryPreferences prefs) { return new ApiKeyStore(prefs, FAKE); }
  @Test void missingKeyIsAbsentWithoutWriting() {
    var prefs = new MemoryPreferences(); var store = store(prefs); assertTrue(store.load().isEmpty());
    assertFalse(store.hasSavedKey()); assertEquals(ApiKeyStore.StoredState.ABSENT, store.storedState()); assertEquals(0, prefs.flushes);
  }
  @Test void saveFlushesProtectedValueAndReconstructionLoadsIt() {
    var prefs = new MemoryPreferences(); store(prefs).save("  fake-d09-key  ");
    assertFalse(prefs.values.get(KEY).contains("fake-d09-key")); assertEquals(1, prefs.flushes);
    var restored = store(prefs.restart()); assertEquals("fake-d09-key", restored.load().orElseThrow());
    assertEquals(ApiKeyStore.StoredState.PRESENT, restored.storedState());
  }
  @Test void deletionFlushesAndSurvivesReconstruction() {
    var prefs = new MemoryPreferences(); var store = store(prefs); store.save("fake-key"); store.delete();
    assertEquals(2, prefs.flushes); assertFalse(store.hasSavedKey()); assertTrue(store(prefs.restart()).load().isEmpty());
    store.delete(); assertEquals(3, prefs.flushes);
  }
  @Test void blankOrNullSaveDoesNotEraseExistingKey() {
    var prefs = new MemoryPreferences(); var store = store(prefs); store.save("fake-key");
    for (String key : new String[]{"", "  ", null}) assertThrows(IllegalArgumentException.class, () -> store.save(key));
    assertEquals("fake-key", store.load().orElseThrow()); assertEquals(1, prefs.flushes);
  }
  @Test void corruptBase64IsUnreadableRatherThanMissing() {
    var prefs = new MemoryPreferences(); prefs.values.put(KEY, "not-base64!"); var store = store(prefs);
    assertEquals(ApiKeyStore.StoredState.UNREADABLE, store.storedState());
    assertEquals("无法读取已保存的 API Key", assertThrows(IllegalStateException.class, store::load).getMessage());
    store.delete(); assertEquals(ApiKeyStore.StoredState.ABSENT, store.storedState());
  }
  @Test void protectionFailureKeepsPreviousValueAndNeverClaimsSave() {
    var prefs = new MemoryPreferences(); store(prefs).save("fake-old"); String before = prefs.values.get(KEY);
    var broken = new ApiKeyStore(prefs, new ApiKeyStore.Protection() {
      public byte[] protect(byte[] b) { throw new IllegalStateException("synthetic external secret"); }
      public byte[] unprotect(byte[] b) { throw new IllegalStateException("synthetic decrypt failure"); }
    });
    var error = assertThrows(IllegalStateException.class, () -> broken.save("fake-new"));
    assertEquals("无法保存 API Key，未确认持久化成功", error.getMessage()); assertEquals(before, prefs.values.get(KEY));
    assertEquals(ApiKeyStore.StoredState.UNREADABLE, broken.storedState());
  }
  @Test void invalidUtf8AndBlankPlaintextAreUnreadable() {
    for (byte[] plain : new byte[][]{new byte[]{(byte) 0xC3, (byte) 0x28}, "  ".getBytes(StandardCharsets.UTF_8), new byte[0]}) {
      var prefs = new MemoryPreferences(); prefs.values.put(KEY, Base64.getEncoder().encodeToString(FAKE.protect(plain)));
      assertEquals(ApiKeyStore.StoredState.UNREADABLE, store(prefs).storedState());
    }
  }
  @Test void backendReadFailureIsUnreadableAndNotEmpty() {
    var prefs = new MemoryPreferences(); prefs.failRead = true; var store = store(prefs);
    assertEquals(ApiKeyStore.StoredState.UNREADABLE, store.storedState());
    assertThrows(IllegalStateException.class, store::load); assertThrows(IllegalStateException.class, () -> store.save("fake-key"));
    assertThrows(IllegalStateException.class, store::delete);
  }
  @Test void swallowedSpiReadFailureLooksAbsentAndDoesNotInventAKey() {
    var prefs = new MemoryPreferences(); prefs.failSpiRead = true;
    assertEquals(ApiKeyStore.StoredState.ABSENT, store(prefs).storedState());
    assertTrue(store(prefs).load().isEmpty()); assertEquals(0, prefs.flushes);
  }
  @Test void writeFailureDoesNotClaimSuccessAndRetryWorks() {
    var prefs = new MemoryPreferences(); var store = store(prefs); prefs.failWrite = true;
    assertThrows(IllegalStateException.class, () -> store.save("fake-key")); prefs.failWrite = false;
    store.save("fake-key"); assertEquals("fake-key", store(prefs.restart()).load().orElseThrow());
  }
  @Test void saveFlushFailureRollsBackMemoryAndReportsUncertainty() {
    var prefs = new MemoryPreferences(); var store = store(prefs); store.save("fake-old"); prefs.failFlush = true;
    assertThrows(IllegalStateException.class, () -> store.save("fake-new"));
    assertEquals("fake-old", store.load().orElseThrow()); assertEquals("fake-old", store(prefs.restart()).load().orElseThrow());
    prefs.failFlush = false; store.save("fake-new"); assertEquals("fake-new", store(prefs.restart()).load().orElseThrow());
  }
  @Test void deleteFlushFailureKeepsOldKeyAndRetryCanDelete() {
    var prefs = new MemoryPreferences(); var store = store(prefs); store.save("fake-old"); prefs.failFlush = true;
    var error = assertThrows(IllegalStateException.class, store::delete);
    assertEquals("无法删除 API Key，未确认持久化成功", error.getMessage());
    assertEquals("fake-old", store.load().orElseThrow()); assertEquals("fake-old", store(prefs.restart()).load().orElseThrow());
    prefs.failFlush = false; store.delete(); assertTrue(store(prefs.restart()).load().isEmpty());
  }
  @Test void nullDependenciesAreRejected() {
    assertThrows(NullPointerException.class, () -> new ApiKeyStore(null, FAKE));
    assertThrows(NullPointerException.class, () -> new ApiKeyStore(new MemoryPreferences(), null));
  }
}
