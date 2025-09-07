package tankbase;

import static tankbase.AbstractTankBase.sysout;

public class Chrono {
    private static long start;
    private static long inter;

    public static void resetChrono() {
        start = inter = System.nanoTime();
    }

    public static void getChrono(String msg) {
        long now = System.nanoTime();

        sysout.printf("%s: %d (%d from begin)%n", msg, now-inter,now-start);
        inter = now;
    }
}
