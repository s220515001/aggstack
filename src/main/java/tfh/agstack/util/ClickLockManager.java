package tfh.agstack.util;

public class ClickLockManager {
    private static long lockEndTime = 0;
    private static final int LOCK_DURATION_MS = 200; // 200毫秒锁定

    public static void lock() {
        lockEndTime = System.currentTimeMillis() + LOCK_DURATION_MS;
    }

    public static boolean isLocked() {
        if (lockEndTime == 0) return false;
        if (System.currentTimeMillis() >= lockEndTime) {
            lockEndTime = 0;
            return false;
        }
        return true;
    }

    public static void unlock() {
        lockEndTime = 0;
    }
}