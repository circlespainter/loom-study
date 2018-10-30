package com.dtcs.study.loom;

/**
 * @test
 * @run testng Basic
 * @summary Additional java.lang.Fiber tests
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * @author Fabio Tudone
 */
@Test
public class FiberTest {
    @DataProvider(name = "executors")
    public static Object[][] data() {
        return new Object[][] {
            {Executors.newWorkStealingPool()},
            {Executors.newFixedThreadPool(1)}
        };
    }

    @Test(dataProvider = "executors")
    public void testDumpStackWaitingFiberWhenCalledFromFiber(Executor scheduler) throws Exception {
        var flag = new AtomicBoolean(false);

        var fiberThreadRef = new AtomicReference<Thread>();
        var fiber = new java.lang.Fiber(scheduler, new Runnable() {
            @Override
            public void run() {
                fiberThreadRef.set(Thread.currentThread());
                try {
                    foo();
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            private void foo() throws InterruptedException {
                while (!flag.get())
                    Thread.sleep(10);
            }
        }).schedule();

        Thread.sleep(100);

        var res = new CompletableFuture<Void>();
        var fiber2 = new java.lang.Fiber(scheduler, () -> {
            try {
                assertNotNull(fiber);
                assertNotNull(fiberThreadRef.get());
                assertTrue(fiber.isAlive());
                assertTrue(fiberThreadRef.get().isAlive());

                var st = fiberThreadRef.get().getStackTrace();
                assertNotNull(st);
                assertTrue(st.length > 0, "The fiber stack size is not > 0;");

                var found = false;
                for (final var ste : st) {
                    if (ste.getMethodName().equals("foo")) {
                        found = true;
                        break;
                    }
                }
                assertTrue(found);

                res.complete(null);
            } catch (final AssertionError e) {
                e.printStackTrace();
                res.completeExceptionally(e);
            }
        }).schedule();

        fiber2.await();
        flag.set(true);
        try {
            assertNull(res.get());
        } catch (final Exception e) {
            fail(e.getMessage());
        }
    }
}
