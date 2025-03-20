package com.example.demo.mt;

import org.junit.jupiter.api.Test;

/**
 * Volatile关键字重排序验证测试类
 * 用于演示volatile关键字如何防止指令重排序并保证内存可见性
 */
public class VolatileReorderingTest {
    // 普通变量，可能被编译器或处理器进行指令重排序
    private int a = 0;
    private int b = 0;
    // 使用volatile修饰的标志变量
    // 1. 保证不同线程间的可见性
    // 2. 建立内存屏障防止指令重排序
    private volatile boolean flag = false;
    
    /**
     * 写线程执行方法
     * 执行顺序：
     * 1. 写普通变量a
     * 2. 写普通变量b
     * 3. 写volatile变量flag（建立内存屏障，保证前面写操作对其他线程可见）
     */
    public void writer() {
        a = 1;
        b = 2;
        flag = true;  // volatile写操作，确保之前的写操作不会被重排序到该操作之后
    }
    
    /**
     * 读线程执行方法
     * 执行顺序：
     * 1. 读volatile变量flag（建立内存屏障，保证后续读操作从主内存读取）
     * 2. 读普通变量a和b
     */
    public void reader() {
        if (flag) {  // volatile读操作，确保之后的读操作不会被重排序到该操作之前
            System.out.println("a = " + a + ", b = " + b);
        }
    }
    
    /**
     * 多线程测试方法
     * 执行10,000次测试来验证：
     * 1. 是否存在指令重排序（打印结果非a=1,b=2的情况）
     * 2. 验证volatile的可见性保证
     */
    @Test
    public void testReordering() throws InterruptedException {
        for (int i = 0; i < 10000; i++) {
            VolatileReorderingTest test = new VolatileReorderingTest();
            
            // 创建写线程和读线程（使用lambda表达式实现Runnable）
            Thread writerThread = new Thread(() -> test.writer());
            Thread readerThread = new Thread(() -> test.reader());
            
            // 启动线程
            writerThread.start();
            readerThread.start();
            
            // 等待线程执行完成
            writerThread.join();
            readerThread.join();
        }
    }
}