package com.easytranslate.config;

import java.util.EnumMap;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Existing appearance keys only. Loading never rewrites corrupt or missing data. */
public final class AppearancePreferences {
  public enum Setting {
    ALWAYS_ON_TOP("alwaysOnTop", "true"), SHOW_SOURCE("showSource", "true"),
    DECORATIONS("decorations", "true"), MOTION_ENABLED("motionEnabled", "true"),
    FONT_SIZE("fontSize", "22"), WINDOW_SIZE("windowSize", "LARGE");
    private final String key, fallback;
    Setting(String key, String fallback) { this.key = key; this.fallback = fallback; }
    private boolean valid(String value) {
      if (value == null) return false;
      return switch (this) {
        case FONT_SIZE -> {
          try { int size = Integer.parseInt(value); yield size >= 16 && size <= 26; }
          catch (NumberFormatException error) { yield false; }
        }
        case WINDOW_SIZE -> value.equals("SMALL") || value.equals("MEDIUM") || value.equals("LARGE");
        default -> value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
      };
    }
  }

  public record Snapshot(boolean alwaysOnTop, boolean showSource, boolean decorations,
      boolean motionEnabled, int fontSize, String windowSize, boolean recovered) {}
  private final Preferences preferences;
  public AppearancePreferences(Preferences preferences) { this.preferences = Objects.requireNonNull(preferences); }

  public Snapshot load() {
    var values = new EnumMap<Setting, String>(Setting.class);
    boolean recovered = false;
    for (Setting setting : Setting.values()) {
      String value;
      try {
        value = preferences.get(setting.key, null);
        if (value != null && !setting.valid(value)) { value = null; recovered = true; }
      } catch (RuntimeException error) { value = null; recovered = true; }
      values.put(setting, value == null ? setting.fallback : value);
    }
    return new Snapshot(Boolean.parseBoolean(values.get(Setting.ALWAYS_ON_TOP)),
        Boolean.parseBoolean(values.get(Setting.SHOW_SOURCE)), Boolean.parseBoolean(values.get(Setting.DECORATIONS)),
        Boolean.parseBoolean(values.get(Setting.MOTION_ENABLED)), Integer.parseInt(values.get(Setting.FONT_SIZE)),
        values.get(Setting.WINDOW_SIZE), recovered);
  }

  /** Acknowledges persistence only after flush; a failed write can still affect the current backend cache. */
  public boolean save(Setting setting, String value) {
    Objects.requireNonNull(setting);
    if (!setting.valid(value)) throw new IllegalArgumentException("Invalid appearance setting");
    try { preferences.put(setting.key, value); preferences.flush(); return true; }
    catch (RuntimeException | BackingStoreException error) { return false; }
  }
}
