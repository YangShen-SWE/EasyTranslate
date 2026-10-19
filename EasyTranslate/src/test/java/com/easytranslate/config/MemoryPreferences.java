package com.easytranslate.config;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/** In-memory backend only: no registry, user root or real key access. */
final class MemoryPreferences extends AbstractPreferences {
  final Map<String, String> values = new HashMap<>();
  final Map<String, String> persisted = new HashMap<>();
  boolean failRead, failSpiRead, failWrite, failFlush;
  int flushes;
  MemoryPreferences() { super(null, ""); }
  MemoryPreferences restart() {
    var next = new MemoryPreferences();
    next.values.putAll(persisted); next.persisted.putAll(persisted); return next;
  }
  @Override public String get(String key, String def) {
    if (failRead) throw new IllegalStateException("synthetic observable read failure");
    return super.get(key, def);
  }
  protected String getSpi(String key) { if (failSpiRead) throw new IllegalStateException("synthetic swallowed SPI failure"); return values.get(key); }
  protected void putSpi(String key, String value) { if (failWrite) throw new IllegalStateException("synthetic write failure"); values.put(key, value); }
  protected void removeSpi(String key) { if (failWrite) throw new IllegalStateException("synthetic delete failure"); values.remove(key); }
  protected void removeNodeSpi() { values.clear(); }
  protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
  protected String[] childrenNamesSpi() { return new String[0]; }
  protected AbstractPreferences childSpi(String name) { throw new UnsupportedOperationException("No children required"); }
  protected void syncSpi() {}
  protected void flushSpi() throws BackingStoreException {
    flushes++; if (failFlush) throw new BackingStoreException("synthetic flush failure");
    persisted.clear(); persisted.putAll(values);
  }
}
