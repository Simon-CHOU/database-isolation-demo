package com.example.demo.mt;

import org.junit.jupiter.api.Test;

/**
 * 指令重排序演示测试类
 * 演示多线程环境下由于指令重排序和内存可见性问题导致的意外执行结果
 *
 * 观察重点：
 *
 * 1. 在不使用 volatile 的版本中：
 *
 *    - 可能会观察到 a=0, b=2 或 a=1, b=0 的输出
 *    - 这表明 writer() 方法中的赋值操作被重排序了
 * 2. 在使用 volatile 的版本中：
 *
 *    - 输出一定是 a=1, b=2
 *    - 在字节码中可以看到 volatile 写操作会生成 StoreStore 屏障
 *    - 在汇编代码中可以看到内存屏障指令（如 MFENCE）
 * 3. 通过字节码对比：
 *
 *    - 非 volatile 版本的 flag 赋值使用普通 putfield 指令
 *    - volatile 版本的 flag 赋值会有额外的内存屏障指令
 * 4. 在 volatile 版本中，可以观察到：
 *
 *    - StoreStore 屏障：确保 volatile 写之前的普通写不会被重排序到 volatile 写之后
 *    - StoreLoad 屏障：确保 volatile 写之后的读操作不会被重排序到 volatile 写之前
 * 这个实验可以清楚地展示 volatile 是如何通过内存屏障来防止指令重排序的。通过大量循环测试，我们可以增加观察到重排序效果的机会。
 */
public class ReorderingTest {
    private int a = 0;
    private int b = 0;
    // 不使用volatile修饰，无法保证可见性和禁止指令重排序
    private boolean flag = false;
    
    /**
     * 写操作方法，存在可能被重排序的三步操作：
     * 1. 写a=1（普通写）
     * 2. 写b=2（普通写）
     * 3. 写flag=true（普通写，但可能被重排序到前面）
     */
    public void writer() {
        a = 1;          // 普通写操作，可能被重排序
        b = 2;          // 普通写操作，可能被重排序
        flag = true;    // 关键标志位写操作（可能被重排序到前两步之前）
    }
    
    /**
     * 读操作方法，可能看到不一致的中间状态：
     * 当flag为true时，理论上应该看到a=1且b=2
     * 但由于可见性和重排序问题，可能观察到：
     * 1. a=0（写操作未可见）
     * 2. b=0（写操作未可见）
     * 3. 或者flag为true时a/b的写入尚未完成
     */
    public void reader() {
        if (flag) {
            // 可能输出异常结果，例如：a=1但b=0，或a=0但b=2，或a=0且b=0
            System.out.println("a = " + a + ", b = " + b);
        }
    }
    
    /**
     * 测试指令重排序的并发测试方法
     * 通过多次运行（10000次）增加观察到重排序现象的概率
     * 注意：实际运行可能不会出现异常结果，这取决于JVM实现和硬件架构
     * @throws InterruptedException 线程中断异常
     */
    @Test
    public void testReordering() throws InterruptedException {
        for (int i = 0; i < 10000; i++) {
            ReorderingTest test = new ReorderingTest();
            
            // 创建并启动写线程和读线程
            Thread writerThread = new Thread(() -> test.writer());
            Thread readerThread = new Thread(() -> test.reader());
            
            writerThread.start();
            readerThread.start();
            
            // 等待两个线程执行完成
            writerThread.join();
            readerThread.join();
        }
    }
}