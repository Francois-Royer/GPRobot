package tankbase.wave;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

import static tankbase.Constant.TANK_SIZE;

public class WaveLog {

    private static final Set<Wave> log = new HashSet<>();

    private WaveLog() {
    }

    public static Optional<Wave> getWave(String sourceName, Point2D.Double p, long tick) {
        return log.stream()
                .filter(w -> w.getSource().getName().equals(sourceName))
                .min(new WaveComparator(p, tick));
    }

    public static Collection<Wave> getWaves() {
        return Collections.unmodifiableCollection(log);
    }

    public static boolean logWave(Wave wave) {
        return log.add(wave);
    }

    public static void updateWaves(Point2D.Double tankPos, long now) {
        List<Wave> toRemove = log.stream().filter(w -> w.getDistance(now) > w.distance(tankPos) + TANK_SIZE / 2).toList();
        toRemove.forEach(log::remove);
    }

    public static boolean removeWave(Wave wave) {
        return log.remove(wave);
    }

    public static void clearWaveLog() {
        log.clear();
    }

    static class WaveComparator implements Comparator<Wave> {
        Point.Double p;
        long tick;

        public WaveComparator(Point2D.Double p, long tick) {
            this.p = p;
            this.tick = tick;
        }

        @Override
        public int compare(Wave w1, Wave w2) {
            return (int) (p.distance(w1.getPosition(tick)) - p.distance(w2.getPosition(tick)));
        }
    }
}
