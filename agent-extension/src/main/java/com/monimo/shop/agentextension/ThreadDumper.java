package com.monimo.shop.agentextension;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Clock;
import java.time.Instant;

/**
 * 이 JVM 의 스레드 덤프를 jstack 비슷한 글로 만든다.
 *
 * <ul>
 *   <li>ThreadInfo.toString() 은 스택을 8줄에서 자른다. 깊은 호출(Spring · JDBC)에서 우리 코드 줄이 안 보일 수 있어 직접 만든다
 *   <li>스택은 스레드마다 128줄까지 (MAX_DEPTH). 원인은 위쪽(지금 실행 중인 줄)에 있다
 *   <li>덤프는 모든 스레드를 잠깐 멈춘다(safepoint). 실측 : 결제 스레드 30개 약 15 ms, 주문 스레드 42개 66 ms (#34).
 *       락을 쥔 synchronizer 를 찾느라 힙을 훑어서(jstack -l 과 같음) 힙이 크면 더 길 수 있다. 10초 안에 또 오면 직전 결과를 다시 준다 (연타 보호)
 * </ul>
 */
final class ThreadDumper {

    static final int MAX_DEPTH = 128;
    static final long REUSE_MILLIS = 10_000;

    record Dump(Instant takenAt, long elapsedMs, int threadCount, String text) {}

    private final ThreadMXBean mx;
    private final Clock clock;
    private Dump last;

    ThreadDumper() {
        this(ManagementFactory.getThreadMXBean(), Clock.systemUTC());
    }

    ThreadDumper(ThreadMXBean mx, Clock clock) {
        this.mx = mx;
        this.clock = clock;
    }

    /** 일꾼 스레드 하나만 부르지만, 테스트 · 안전을 위해 동기화한다 */
    synchronized Dump dump() {
        Instant now = clock.instant();
        if (last != null && now.toEpochMilli() - last.takenAt().toEpochMilli() < REUSE_MILLIS) {
            return last;
        }
        long t0 = System.nanoTime();
        ThreadInfo[] infos = mx.dumpAllThreads(mx.isObjectMonitorUsageSupported(), mx.isSynchronizerUsageSupported(), MAX_DEPTH);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        StringBuilder b = new StringBuilder(infos.length * 1024);
        for (ThreadInfo info : infos) {
            if (info != null) format(info, b);
        }
        last = new Dump(now, elapsedMs, infos.length, b.toString());
        return last;
    }

    /** jstack 모양 한 스레드. ThreadInfo.toString() 과 같은 순서지만 스택을 자르지 않는다 */
    static void format(ThreadInfo t, StringBuilder b) {
        b.append('"').append(t.getThreadName()).append("\" #").append(t.getThreadId());
        if (t.isDaemon()) b.append(" daemon");
        b.append(" prio=").append(t.getPriority()).append(' ').append(t.getThreadState());
        if (t.getLockName() != null) b.append(" on ").append(t.getLockName());
        if (t.getLockOwnerName() != null) {
            b.append(" owned by \"").append(t.getLockOwnerName()).append("\" #").append(t.getLockOwnerId());
        }
        if (t.isSuspended()) b.append(" (suspended)");
        if (t.isInNative()) b.append(" (in native)");
        b.append('\n');

        StackTraceElement[] stack = t.getStackTrace();
        MonitorInfo[] monitors = t.getLockedMonitors();
        for (int i = 0; i < stack.length; i++) {
            b.append("\tat ").append(stack[i]).append('\n');
            if (i == 0 && t.getLockInfo() != null) {
                String verb = switch (t.getThreadState()) {
                    case BLOCKED -> "- blocked on ";
                    case WAITING, TIMED_WAITING -> "- waiting on ";
                    default -> null;
                };
                if (verb != null) b.append('\t').append(verb).append(t.getLockInfo()).append('\n');
            }
            for (MonitorInfo m : monitors) {
                if (m.getLockedStackDepth() == i) b.append("\t- locked ").append(m).append('\n');
            }
        }
        if (stack.length == MAX_DEPTH) b.append("\t...\n");

        LockInfo[] syncs = t.getLockedSynchronizers();
        if (syncs.length > 0) {
            b.append("\n\tLocked ownable synchronizers:\n");
            for (LockInfo l : syncs) b.append("\t- ").append(l).append('\n');
        }
        b.append('\n');
    }
}
