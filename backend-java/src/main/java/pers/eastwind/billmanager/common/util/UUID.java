package pers.eastwind.billmanager.common.util;


public class UUID {
    private static int seq = 0;

    /**
     * 生成时间有序 int
     */
    public static int genIntID() {
        synchronized(UUID.class) {
            int timeBit = (int)(System.currentTimeMillis() & 0x3FFFFFF) << 5;
            seq++;
            if (seq >= 1 << 5) {
                seq = 0;
            }
            return timeBit | seq;
        }
    }
}
