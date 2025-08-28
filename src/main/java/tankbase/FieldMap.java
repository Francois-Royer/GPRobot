package tankbase;

import tankbase.enemy.Enemy;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.List;

import static java.lang.Math.*;
import static tankbase.AbstractTankBase.*;
import static tankbase.Constant.BORDER_OFFSET;
import static tankbase.Constant.MAX_DANGER_ZONE;
import static tankbase.TankUtils.collisionCircleSegment;
import static tankbase.TankUtils.range;
import static tankbase.WaveLog.getWaves;

public class FieldMap {
    private static int width;
    private static int height;
    private static double scale;


    private static double[][] map;
    private static double[][] battleZoneMap;
    private static double maxDanger;

    public static void initFieldMap() {
        scale = sqrt(FIELD_WIDTH * FIELD_HEIGHT / MAX_DANGER_ZONE);
        width = (int) (FIELD_WIDTH / scale);
        height = (int) (FIELD_HEIGHT / scale);
        setBattleZoneToField();
    }

    public static void setBattleZoneToField() {
        double a = (double) width / 2;
        double b = (double) height / 2;
        double e = sqrt(1 - b * b / a / a); // exentricity
        double f = e * a; // focal distance
        Point c1 = new Point((int) ((double) width / 2 - f), height / 2);
        Point c2 = new Point((int) ((double) width / 2 + f), height / 2);
        setBattleZone(a, b, c1, c2);
    }

    public void setBattleZone(Point c, double r) {
        setBattleZone(r, r, c, c);
    }

    public static void computeDangerMap(Collection<Enemy> enemies, double maxEnemyDamage, long now) {
        clear();
        maxDanger = 1;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                double danger = 0;
                for (Enemy enemy : enemies)
                    if (enemy.isAlive() && enemy.getLastScan() > 0)
                        danger += enemy.getDanger(x, y, maxEnemyDamage);
                for (Wave wave : getWaves())
                    danger += wave.getDanger(x, y, now);
                maxDanger = max(danger, maxDanger);
                map[x][y] = danger;
            }
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                for (Enemy enemy : enemies)
                    if (enemy.isAlive() && enemy.getLastScan() > 0 && enemy.isMaxDanger(x, y))
                        map[x][y] = maxDanger;

                map[x][y] /= maxDanger;
                map[x][y] = max(map[x][y], battleZoneMap[x][y]);
            }
    }

    public static Point2D.Double computeSafePosition(TankState state, Collection<Enemy> enemies) {
        int dist = 8;
        Point2D.Double safePosition = state;
        Point gp = new Point((int) (state.getX() / scale), (int) (state.getY() / scale));
        List<Point> points = TankUtils.listClosePoint(gp, dist, width, height);
        double danger = Double.MAX_VALUE;
        for (Point p : points) {
            double d = computeMoveDanger(gp, p);
            double x = max(BORDER_OFFSET, min(FIELD_WIDTH - BORDER_OFFSET, p.getX() * scale + scale / 2));
            double y = max(BORDER_OFFSET, min(FIELD_HEIGHT - BORDER_OFFSET, p.getY() * scale + scale / 2));
            Point2D.Double position = new Point2D.Double(x, y);
            // add danger if collision with enemy
            d += enemies.stream().filter(e -> e.isAlive() &&
                    collisionCircleSegment(e.getState(), Constant.TANK_SIZE*1.1, state,
                                           position)).mapToDouble(e -> 100).sum();
            if (d < danger) {
                danger = d;
                safePosition = position;
            }
        }
        return safePosition;
    }


    public static double computeMoveDanger(Point from, Point to) {
        double d = from.distance(to);
        if (d == 0) return Double.MAX_VALUE;
        double danger = 0;
        for (int p = 0; p < d; p++) {
            int x = from.x + (int) (p * (to.x - from.x) / d);
            int y = from.y + (int) (p * (to.y - from.y) / d);
            danger += map[x][y];
        }
        return danger / pow(d, 1.1);
    }

    public static double[][] getMap() {
        return map;
    }

    public static int getWidth() {
        return width;
    }

    public static int getHeight() {
        return height;
    }


    public static double getScale() {
        return scale;
    }

    @Override
    public String toString() {
        return String.format("FieldMap[scale=%.2f, width=%d, height=%d]", scale, width, height);
    }

    // ////////////////////////////////////////////////////////////////////////
    private static void setBattleZone(double a, double b, Point c1, Point c2) {
        Point o = new Point((int) (c1.getX() - a / 2), (int) (c1.getY() - b / 2));
        double rMax = o.distance(c1) + o.distance(c2);

        battleZoneMap = new double[width][height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                Point p = new Point(x, y);
                double d = p.distance(c1) + p.distance(c2);
                if (d > rMax)
                    battleZoneMap[x][y] = 1;
                else if (d > 2 * a)
                    battleZoneMap[x][y] = pow(range(d, 2 * b, rMax, 0, 1), 4);
                else
                    battleZoneMap[x][y] = 0;
            }
    }

    private static void clear() {
        maxDanger = 0;
        map = new double[width][height];
    }

    private FieldMap() {}

    }
