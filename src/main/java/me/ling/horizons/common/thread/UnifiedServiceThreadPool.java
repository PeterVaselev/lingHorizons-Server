package me.ling.horizons.common.thread;

import me.ling.horizons.common.Logger;
import me.ling.horizons.common.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UnifiedServiceThreadPool {
    public final ServiceManager serviceManager;
    public final MultiThreadPrioritySemaphore groupSemaphore;

    private final MultiThreadPrioritySemaphore.Block selfBlock;
    private final ThreadGroup dedicatedPool;
    private final List<Thread> threads = new ArrayList<>();
    private int threadId = 0;

    public UnifiedServiceThreadPool() {
        this.dedicatedPool = new ThreadGroup("Voxy Dedicated Service");
        this.serviceManager = new ServiceManager(this::release);
        this.groupSemaphore = new MultiThreadPrioritySemaphore(this.serviceManager::tryRunAJob);

        this.selfBlock = this.groupSemaphore.createBlock();
    }

    private final void release(int i) {this.groupSemaphore.pooledRelease(i);}

    public boolean setNumThreads(int threads) {
        synchronized (this.threads) {
            this.threads.removeIf(t -> !t.isAlive());
            int diff = threads - this.threads.size();
            if (diff == 0) return false;
            if (diff < 0) {
                this.selfBlock.release(-diff);
            } else {
                for (int i = 0; i < diff; i++) {
                    var t = new Thread(this.dedicatedPool, this::workerThread, "Dedicated Voxy Worker #" + (this.threadId++));
                    t.setPriority(3);
                    t.setDaemon(true);
                    this.threads.add(t);
                    t.start();
                }
            }
        }
        long timeout = System.currentTimeMillis() + 500;
        while (System.currentTimeMillis() < timeout) {
            synchronized (this.threads) {
                this.threads.removeIf(t -> !t.isAlive());
                if (this.threads.size() == threads) return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                break;
            }
        }
        return true;
    }

    private void workerThread() {
        try {
            this.selfBlock.acquire();
        } catch (Throwable t) {
            Logger.error("Exception in worker thread: " + Thread.currentThread().getName(), t);
        } finally {
            synchronized (this.threads) {
                this.threads.remove(Thread.currentThread());
            }
        }
    }

    public void shutdown() {
        this.serviceManager.shutdown();
        this.selfBlock.release(10000);

        synchronized (this.threads) {
            for (Thread t : this.threads) {
                if (t != null && t.isAlive()) {
                    t.interrupt();
                }
            }
        }

        long deadline = System.currentTimeMillis() + 500;
        while (System.currentTimeMillis() < deadline) {
            synchronized (this.threads) {
                this.threads.removeIf(t -> !t.isAlive());
                if (this.threads.isEmpty()) {
                    break;
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                break;
            }
        }

        synchronized (this.threads) {
            this.threads.clear();
        }

        try {
            this.selfBlock.free();
        } catch (Exception ignored) {}
    }


    public static void main(String[] args) {
        var ustp = new UnifiedServiceThreadPool();

        AtomicInteger cc = new AtomicInteger();
        AtomicInteger cnt = new AtomicInteger();

        var s1 = ustp.serviceManager.createService(()->{
            AtomicBoolean cleaned = new AtomicBoolean();
            AtomicInteger a = new AtomicInteger();
            return new Pair<>(()->{
                if (cleaned.get()) {
                    System.err.println("TRIED EXECUTING CLEANED CTX");
                } else {
                    a.incrementAndGet();
                    cnt.incrementAndGet();
                }
            }, ()->{
                if (cleaned.getAndSet(true)) {
                    System.err.println("TRIED DOUBLE CLEANING A VALUE");
                } else {
                    System.out.println("Cleaned ref, exec: " + a.get());
                    cc.incrementAndGet();
                }
            });
        }, 1);

        for (int i = 0; i < 1000; i++) {
            s1.execute();
        }
        ustp.setNumThreads(1);
        ustp.setNumThreads(10);
        ustp.setNumThreads(0);
        ustp.setNumThreads(1);
        s1.blockTillEmpty();
        s1.shutdown();
        ustp.shutdown();
        System.out.println(cnt);
    }
}
