package tfh.agstack.util;

/**
 * 用于控制聚合槽整组丢出（Ctrl+Q 或拖拽）时，跳过普通 Q 的拦截逻辑。
 */
public class DropFlag {
    private static final ThreadLocal<Boolean> skipDropProcessing = ThreadLocal.withInitial(() -> false);

    public static void setSkipDropProcessing(boolean skip) {
        skipDropProcessing.set(skip);
    }

    public static boolean shouldSkipDropProcessing() {
        return skipDropProcessing.get();
    }

    public static void clear() {
        skipDropProcessing.remove();
    }
}