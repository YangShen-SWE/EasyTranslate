package com.easytranslate.service.hotkey;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HotkeySessionTest {
  static class Fake implements GlobalHotkeyService {
    Runnable callback; int starts, closes; boolean failStart, failClose;
    public void start(Runnable trigger) { callback=trigger; starts++; if(failStart) throw new IllegalStateException("fake start"); }
    public void close() { closes++; if(failClose) throw new IllegalStateException("fake close"); }
  }
  @Test void repeatedActivationUsesFreshServiceAndRejectsOldCallbacks() {
    var created = new ArrayList<Fake>(); var count=new AtomicInteger();
    var session=new HotkeySession(()->{var f=new Fake();created.add(f);return f;});
    for(int i=0;i<10;i++) { session.start(count::incrementAndGet); session.start(count::incrementAndGet);
      var f=created.getLast(); f.callback.run(); session.close(); session.close(); f.callback.run();
      assertEquals(1,f.starts);assertEquals(1,f.closes); }
    assertEquals(10,count.get());assertEquals(10,created.size());
  }
  @Test void failedStartClosesAndRevokesBeforeRetry() {
    var f=new Fake();f.failStart=true;var count=new AtomicInteger();var session=new HotkeySession(()->f);
    assertThrows(IllegalStateException.class,()->session.start(count::incrementAndGet));
    f.callback.run();assertEquals(0,count.get());assertEquals(1,f.closes);
    f.failStart=false; session.start(count::incrementAndGet);f.callback.run();assertEquals(1,count.get());session.close();
  }
  @Test void cleanupFailureDoesNotHideStartupFailure() {
    var f=new Fake();f.failStart=true;f.failClose=true;
    var failure=assertThrows(IllegalStateException.class,()->new HotkeySession(()->f).start(()->{}));
    assertEquals("fake start",failure.getMessage());assertEquals(1,failure.getSuppressed().length);
  }
  @Test void closeFailureStillRevokesOldNotification() {
    var f=new Fake();f.failClose=true;var count=new AtomicInteger();var session=new HotkeySession(()->f);
    session.start(count::incrementAndGet);assertThrows(IllegalStateException.class,session::close);
    f.callback.run();assertEquals(0,count.get());session.close();assertEquals(1,f.closes);
  }
  @Test void closingBeforeStartIsHarmless() { var s=new HotkeySession(Fake::new);s.close();s.start(()->{});s.close(); }
  @Test void closedNativeInstanceCannotBeRestartedWithoutInstallingHook() {
    var nativeService=new WindowsTabObserverService();nativeService.close();nativeService.close();
    assertThrows(IllegalStateException.class,()->nativeService.start(()->fail("must not trigger")));
  }
}
