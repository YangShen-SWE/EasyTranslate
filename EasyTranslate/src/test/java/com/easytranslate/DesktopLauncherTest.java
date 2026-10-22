package com.easytranslate;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DesktopLauncherTest {
  @Test void ordinaryLaunchDelegatesExactlyOnce() {
    var calls=new AtomicInteger();
    assertEquals(0,DesktopLauncher.run(new String[0],calls::incrementAndGet,System.out));assertEquals(1,calls.get());
  }
  @Test void unknownOrCombinedArgumentsNeverLaunchGui() {
    for(String[] args:new String[][]{{"--unknown"},{"--diagnose","extra"}}) {
      var calls=new AtomicInteger();var output=new ByteArrayOutputStream();
      assertEquals(2,DesktopLauncher.run(args,calls::incrementAndGet,new PrintStream(output)));
      assertEquals(0,calls.get());assertTrue(output.toString().contains("Usage:"));
    }
  }
  @Test void unpackagedDiagnosticFailsClosedWithoutGui() {
    var calls=new AtomicInteger();var output=new ByteArrayOutputStream();
    assertEquals(1,DesktopLauncher.run(new String[]{"--diagnose"},calls::incrementAndGet,new PrintStream(output)));
    assertEquals(0,calls.get());assertFalse(output.toString().contains("INSTALLATION_CHECK_PASSED"));
    assertTrue(output.toString().contains("INSTALLATION_CHECK_FAILED"));
  }
}
