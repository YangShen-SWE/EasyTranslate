package com.easytranslate.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.easytranslate.config.AppearancePreferences.Setting;

class AppearancePreferencesTest {
  @Test void missingSettingsUseDefaultsWithoutWriting() {
    var prefs = new MemoryPreferences(); var value = new AppearancePreferences(prefs).load();
    assertTrue(value.alwaysOnTop() && value.showSource() && value.decorations() && value.motionEnabled());
    assertEquals(22, value.fontSize()); assertEquals("LARGE", value.windowSize()); assertFalse(value.recovered());
    assertTrue(prefs.values.isEmpty()); assertEquals(0, prefs.flushes);
  }
  @Test void allSixSettingsAreFlushedAndSurviveBackendReconstruction() {
    var prefs = new MemoryPreferences(); var store = new AppearancePreferences(prefs);
    for (var setting : new Setting[]{Setting.ALWAYS_ON_TOP, Setting.SHOW_SOURCE, Setting.DECORATIONS, Setting.MOTION_ENABLED})
      assertTrue(store.save(setting, "false"));
    assertTrue(store.save(Setting.FONT_SIZE, "26")); assertTrue(store.save(Setting.WINDOW_SIZE, "SMALL"));
    var value = new AppearancePreferences(prefs.restart()).load();
    assertFalse(value.alwaysOnTop() || value.showSource() || value.decorations() || value.motionEnabled());
    assertEquals(26, value.fontSize()); assertEquals("SMALL", value.windowSize()); assertFalse(value.recovered());
    assertEquals(6, prefs.flushes);
  }
  @Test void malformedBooleansFallbackIndividuallyWithoutDestroyingOtherSettings() {
    var prefs = new MemoryPreferences(); prefs.values.put("showSource", "false"); prefs.values.put("motionEnabled", "garbage");
    var value = new AppearancePreferences(prefs).load();
    assertFalse(value.showSource()); assertTrue(value.motionEnabled()); assertTrue(value.recovered());
    assertEquals("garbage", prefs.values.get("motionEnabled")); assertEquals(0, prefs.flushes);
  }
  @Test void malformedAndOutOfRangeFontFallsBackTo22() {
    for (String font : new String[]{"NaN", "Infinity", "15", "27", "-1", "99999999999999", ""}) {
      var prefs = new MemoryPreferences(); prefs.values.put("fontSize", font);
      var value = new AppearancePreferences(prefs).load(); assertEquals(22, value.fontSize()); assertTrue(value.recovered());
    }
  }
  @Test void bothFontBoundariesAndAllWindowSizesLoad() {
    for (String font : new String[]{"16", "26"}) for (String size : new String[]{"SMALL", "MEDIUM", "LARGE"}) {
      var prefs = new MemoryPreferences(); prefs.values.put("fontSize", font); prefs.values.put("windowSize", size);
      var value = new AppearancePreferences(prefs).load(); assertEquals(Integer.parseInt(font), value.fontSize());
      assertEquals(size, value.windowSize()); assertFalse(value.recovered());
    }
  }
  @Test void invalidSizeUsesLargeWithoutRewritingUnknownVersion() {
    var prefs = new MemoryPreferences(); prefs.values.put("windowSize", "FUTURE");
    var value = new AppearancePreferences(prefs).load(); assertEquals("LARGE", value.windowSize());
    assertTrue(value.recovered()); assertEquals("FUTURE", prefs.values.get("windowSize"));
  }
  @Test void readFailuresDoNotPreventSafeDefaultLoad() {
    var prefs = new MemoryPreferences(); prefs.failRead = true;
    var value = assertDoesNotThrow(() -> new AppearancePreferences(prefs).load());
    assertTrue(value.recovered()); assertTrue(value.alwaysOnTop() && value.motionEnabled()); assertEquals(22, value.fontSize());
  }
  @Test void backendSuppressedReadErrorLooksMissingButStillUsesSafeDefaults() {
    var prefs = new MemoryPreferences(); prefs.failSpiRead = true;
    var value = new AppearancePreferences(prefs).load();
    assertEquals(22, value.fontSize()); assertTrue(value.showSource());
    assertFalse(value.recovered()); // AbstractPreferences swallowed it: no observable diagnostic available.
    assertEquals(0, prefs.flushes);
  }
  @Test void writeFailureIsNotAcknowledgedAndNextWriteCanRecover() {
    var prefs = new MemoryPreferences(); var store = new AppearancePreferences(prefs); prefs.failWrite = true;
    assertFalse(store.save(Setting.SHOW_SOURCE, "false")); prefs.failWrite = false;
    assertTrue(store.save(Setting.SHOW_SOURCE, "false")); assertFalse(new AppearancePreferences(prefs.restart()).load().showSource());
  }
  @Test void flushFailureIsNotAcknowledgedEvenWhenMemoryChanged() {
    var prefs = new MemoryPreferences(); var store = new AppearancePreferences(prefs); prefs.failFlush = true;
    assertFalse(store.save(Setting.WINDOW_SIZE, "SMALL")); assertEquals("SMALL", prefs.values.get("windowSize"));
    assertEquals("LARGE", new AppearancePreferences(prefs.restart()).load().windowSize());
    prefs.failFlush = false; assertTrue(store.save(Setting.WINDOW_SIZE, "SMALL"));
    assertEquals("SMALL", new AppearancePreferences(prefs.restart()).load().windowSize());
  }
  @Test void invalidWritesAndNullDependenciesAreRejectedWithoutMutation() {
    var prefs = new MemoryPreferences(); var store = new AppearancePreferences(prefs);
    assertThrows(IllegalArgumentException.class, () -> store.save(Setting.FONT_SIZE, "100"));
    assertThrows(IllegalArgumentException.class, () -> store.save(Setting.WINDOW_SIZE, "small"));
    assertThrows(IllegalArgumentException.class, () -> store.save(Setting.SHOW_SOURCE, "yes"));
    assertThrows(NullPointerException.class, () -> new AppearancePreferences(null));
    assertThrows(NullPointerException.class, () -> store.save(null, "true")); assertTrue(prefs.values.isEmpty());
  }
}
