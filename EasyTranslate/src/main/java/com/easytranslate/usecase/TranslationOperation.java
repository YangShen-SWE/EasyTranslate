package com.easytranslate.usecase;

/** State of the latest trigger, independent of any translation retained on screen. */
public enum TranslationOperation {
  IDLE, READING, TRANSLATING, SUCCEEDED, NO_SELECTION, UNREADABLE, FAILED, ONLINE_DISABLED
}
