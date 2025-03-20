package com.example.demo.mt;

public class MemoryBarrierDemo {
    private static boolean running = true;
    private static volatile boolean volatileRunning = true;

    public static void main(String[] args) throws InterruptedException {
//        testWithoutVolatile();
        testWithVolatile();
    }

    // 示例1: 不使用 volatile，可能会发生 CPU 缓存导致的可见性问题
    private static void testWithoutVolatile() throws InterruptedException {
        running = true;
        Thread worker = new Thread(() -> {
            while (running) { }  // 可能无法检测到 running=false （进入死循环）
            System.out.println("Thread exited (without volatile).");
        });

        worker.start();
        Thread.sleep(1000);
        running = false; // 这个修改可能对 worker 线程不可见
        worker.join();
    }

    // 示例2: 使用 volatile，确保写入对其他线程可见
    private static void testWithVolatile() throws InterruptedException {
        volatileRunning = true;
        Thread worker = new Thread(() -> {
            while (volatileRunning) { } // 一定能检测到 volatileRunning=false
            System.out.println("Thread exited (with volatile).");
        });

        worker.start();
        Thread.sleep(1000);
        volatileRunning = false; // 这个修改对 worker 线程立即可见
        worker.join();
    }
}
