package com.easytranslate.config;

import com.easytranslate.service.translation.TranslationFailure;
import org.junit.jupiter.api.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

class OnlineTranslationPolicyTest {
  private Preferences prefs;
  private OnlineTranslationPolicy policy;
  @BeforeEach void setup() {
    prefs = Preferences.userRoot().node("com/easytranslate/test-consent-" + java.util.UUID.randomUUID());
    policy = new OnlineTranslationPolicy(prefs);
  }
  @AfterEach void cleanup() throws Exception { prefs.removeNode(); prefs.flush(); }

  @Test void freshInstallDoesNotUpload() {
    assertEquals(OnlineTranslationPolicy.Decision.UNDECIDED, policy.decision());
    assertFalse(policy.permit().isAllowed());
    assertThrows(TranslationFailure.class, () -> policy.permit().start(CompletableFuture::new));
  }
  @Test void refusalSurvivesReconstruction() {
    policy.choose(false);
    var restored = new OnlineTranslationPolicy(prefs);
    assertEquals(OnlineTranslationPolicy.Decision.DENIED, restored.decision());
    assertFalse(restored.permit().isAllowed());
  }
  @Test void explicitConsentSurvivesReconstruction() {
    policy.choose(true);
    assertTrue(new OnlineTranslationPolicy(prefs).permit().isAllowed());
  }
  @Test void corruptOrOtherVersionCannotEnable() {
    for (String value : new String[]{"true", "enabled", "v2:enabled", "v1:ENABLED", ""}) {
      prefs.put("onlineTranslationConsent", value);
      assertFalse(new OnlineTranslationPolicy(prefs).permit().isAllowed());
    }
  }
  @Test void disablingRevokesPermitEvenAfterEnablingAgain() {
    policy.choose(true); var old = policy.permit();
    policy.choose(false); policy.choose(true);
    assertFalse(old.isAllowed());
    assertTrue(policy.permit().isAllowed());
  }
  @Test void deniedPermitCannotReviveAfterConsent() {
    var old = policy.permit(); policy.choose(true);
    assertFalse(old.isAllowed());
  }
  @Test void lateAdmissionCannotStartAndDoesNotCallSupplier() {
    policy.choose(true); var permit = policy.permit();
    policy.choose(false);
    AtomicInteger starts = new AtomicInteger();
    var error = assertThrows(TranslationFailure.class, () -> permit.start(() -> {
      starts.incrementAndGet(); return new CompletableFuture<>();
    }));
    assertEquals(TranslationFailure.Kind.ONLINE_DISABLED, error.kind());
    assertEquals(0, starts.get());
  }
  @Test void admittedPendingRequestDoesNotBlockDisable() throws Exception {
    policy.choose(true); var permit = policy.permit();
    var pending = new CompletableFuture<String>();
    assertSame(pending, permit.start(() -> pending));
    CompletableFuture.runAsync(() -> policy.choose(false)).get(2, java.util.concurrent.TimeUnit.SECONDS);
    assertFalse(permit.isAllowed());
    assertFalse(pending.isDone()); // No cancellation or stopped billing is claimed.
    pending.complete("synthetic");
  }
  @Test void removedConfigurationFailsClosedOnSave() throws Exception {
    policy.choose(true); var old = policy.permit();
    prefs.removeNode();
    try {
      assertThrows(IllegalStateException.class, () -> policy.choose(true));
      assertFalse(old.isAllowed());
      assertFalse(policy.permit().isAllowed());
    } finally {
      prefs = Preferences.userRoot().node("com/easytranslate/test-cleanup-" + java.util.UUID.randomUUID());
    }
  }
  @Test void unreadableConfigurationStartsClosed() throws Exception {
    var removed = prefs.node("removed"); removed.removeNode();
    assertFalse(new OnlineTranslationPolicy(removed).permit().isAllowed());
  }
}
