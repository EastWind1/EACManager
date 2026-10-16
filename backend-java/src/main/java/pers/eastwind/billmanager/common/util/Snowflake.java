package pers.eastwind.billmanager.common.util;

/**
 * 雪花算法
 */
public class Snowflake {
    private static int seq = 1;
    private static int preTimeBit = 0;

    /**
     * 生成时间有序 int
     */
    public static int genIntID() {
        synchronized (Snowflake.class) {
            // 取 26 位时间
            int timeBit = (int) (System.currentTimeMillis() & 0x3FFFFFF) << 5;
            seq++;
            // 每毫秒最大 31 个
            if (preTimeBit != timeBit || seq >= 1 << 5) {
                seq = 1;
            }
            preTimeBit = timeBit;
            return timeBit | seq;
        }
    }
}
