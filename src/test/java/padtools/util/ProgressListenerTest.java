package padtools.util;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * ProgressListener のファクトリ動作テスト。
 */
public class ProgressListenerTest {

    @Test
    public void testSilentDoesNothing() {
        ProgressListener l = ProgressListener.silent();
        l.onProgress(1, 2, "x");
        // 例外が出なければ OK
    }

    @Test
    public void testThrottledDropsRapidEvents() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        ProgressListener l = ProgressListener.throttled(
                (d, t, m) -> calls.incrementAndGet(), 100L);
        // 短時間に 5 回呼ぶ → 最初の 1 件だけ通る
        for (int i = 0; i < 5; i++) {
            l.onProgress(i, 10, "x");
        }
        assertEquals("最初の 1 件以外は drop されるはず", 1, calls.get());
        Thread.sleep(120);
        l.onProgress(6, 10, "x");
        assertEquals("間隔をあければさらに 1 件流れる", 2, calls.get());
    }

    @Test
    public void testThrottledAlwaysFiresOnCompletion() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        ProgressListener l = ProgressListener.throttled(
                (d, t, m) -> calls.incrementAndGet(), 10_000L);
        l.onProgress(1, 10, "x");
        l.onProgress(10, 10, "done"); // 完了は throttle されない
        assertEquals(2, calls.get());
    }

    @Test
    public void testThrottledNullDelegateSafe() {
        ProgressListener l = ProgressListener.throttled(null, 100L);
        l.onProgress(1, 2, "x");
        // 例外が出なければ OK
        assertTrue(true);
    }
}
