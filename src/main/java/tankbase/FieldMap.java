package tankbase;

import tankbase.enemy.Enemy;
import tankbase.wave.Wave;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.Collections;

import static java.lang.Math.*;
import static tankbase.AbstractTankBase.*;
import static tankbase.AbstractTankDrawingBase.INFO_LEVEL;
import static tankbase.Constant.BORDER_OFFSET;
import static tankbase.TankUtils.*;
import static tankbase.wave.WaveLog.getWaves;
import static tankbase.enemy.EnemyDB.filterEnemies;

public class FieldMap {
    private static int totalFieldZone = 6000;
    private static int dangerRadius = 16;
    private static boolean fullMap = false;
    private static int width;
    private static int height;
    private static double scale;
    private static double[][] map;
    private static double[][] battleZoneMap;
    private static double maxDanger;
    private static Collection<Point> points = Collections.emptyList();
    private static boolean searchMode=true;
    private static boolean forceRebuildZone;
    private static Point zoneCenter;
    private static double zoneRadius;

    public static void initFieldMap() {
        scale = sqrt(FIELD_WIDTH * FIELD_HEIGHT / totalFieldZone);
        width = (int) (FIELD_WIDTH / scale);
        height = (int) (FIELD_HEIGHT / scale);
        forceRebuildZone = true;
        if (searchMode)
            setBattleZoneToField();
        else
            setBattleZone(zoneCenter, zoneRadius);
        forceRebuildZone = false;
    }

    public static void setBattleZoneToField() {
        if (!searchMode || forceRebuildZone) {
            double a = (double) width / 2;
            double b = (double) height / 2;
            double e = sqrt(1 - b * b / a / a); // eccentricity
            double f = e * a; // focal distance
            Point c1 = new Point((int) ((double) width / 2 - f), height / 2);
            Point c2 = new Point((int) ((double) width / 2 + f), height / 2);
            searchMode = true;
            setBattleZone(a, b, c1, c2);
        }
    }

    public static void setBattleZone(Point c, double r) {
        zoneCenter = c;
        zoneRadius = r;
        searchMode = false;
        setBattleZone(r, r, c, c);
    }

    public static void computeDangerMap(Collection<Enemy> enemies, double maxEnemyDamage, long now, TankState state) {
        if (fullMap)
            computeFullDangerMap(enemies, maxEnemyDamage, now);
        else
            computeNearDangerMap(enemies, maxEnemyDamage, now, state);
    }

    public static Point2D.Double computeSafeDestination(TankState state) {
        Collection<Enemy> enemies = filterEnemies(Enemy::isScanned);
        Point2D.Double safePosition = state;
        Point gp = new Point((int) (state.getX() / scale), (int) (state.getY() / scale));

        if (fullMap)
            points = listClosePoint(gp, dangerRadius, width, height);

        double danger = Double.MAX_VALUE;
        for (Point p : points) {
            double d = computeMoveDanger(gp, p);
            double x = max(BORDER_OFFSET, min(FIELD_WIDTH - BORDER_OFFSET, p.getX() * scale + scale / 2));
            double y = max(BORDER_OFFSET, min(FIELD_HEIGHT - BORDER_OFFSET, p.getY() * scale + scale / 2));
            Point2D.Double position = new Point2D.Double(x, y);
            // add danger if collision with enemy
            d += enemies.stream().filter(e -> e.isAlive() &&
                    collisionCircleSegment(e.getState(), Constant.TANK_SIZE * 1.1, state,
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
        for (double p = 0; p < d; p++) {
            int x = from.x + (int) (p * (to.x - from.x) / d);
            int y = from.y + (int) (p * (to.y - from.y) / d);

            if (x>=0 && x<width && y>=0 && y<width)
                danger += map[x][y];
        }
        return danger / pow(d, 1.1);
    }

    public static Collection<Point> getDangerMapPoints() {
        return points;
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

    public static void toggleMapMode() {
        fullMap = !fullMap;
        if (fullMap) {
            totalFieldZone = 1500;
            dangerRadius = 8;
        } else {
            totalFieldZone = 6000;
            dangerRadius = 16;
        }
        initFieldMap();
    }

    public static boolean isFullMap() {
        return fullMap;
    }

    // ////////////////////////////////////////////////////////////////////////
    private static void computeFullDangerMap(Collection<Enemy> enemies, double maxEnemyDamage, long now) {
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
                if (!BIG_BATTLE_FIELD || !searchMode)
                    map[x][y] = max(map[x][y], battleZoneMap[x][y]);
            }
    }

    private static void computeNearDangerMap(Collection<Enemy> enemies, double maxEnemyDamage, long now,
                                             TankState state) {
        clear();
        maxDanger = 1;
        Point gp = new Point((int) (state.getX() / scale), (int) (state.getY() / scale));
        points = listClosePoint(gp, dangerRadius, width, height);
        points.forEach(p -> {
            double danger = 0;
            for (Enemy enemy : enemies)
                if (enemy.getLastScan() > 0)
                    danger += enemy.getDanger(p.x, p.y, maxEnemyDamage);
            for (Wave wave : getWaves())
                danger += wave.getDanger(p.x, p.y, now);
            maxDanger = max(danger, maxDanger);
            map[p.x][p.y] = danger;
        });

        points.forEach(p -> {
            for (Enemy enemy : enemies)
                if (enemy.isAlive() && enemy.getLastScan() > 0 && enemy.isMaxDanger(p.x, p.y))
                    map[p.x][p.y] = maxDanger;

            map[p.x][p.y] /= maxDanger;
            if (!BIG_BATTLE_FIELD || !searchMode)
                map[p.x][p.y] = max(map[p.x][p.y], battleZoneMap[p.x][p.y]);
        });
    }

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
                else
                    battleZoneMap[x][y] = pow(range(d, 0, rMax, 0, 1), 8);
            }

        if (INFO_LEVEL > 1)
            sysout.println(String.format("FieldMap[scale=%.2f, width=%d, height=%d, searchMode=%b, a=%.2f, b=%.2f]",
                scale, width, height, searchMode, a, b));
    }

    private static void clear() {
        maxDanger = 0;
        map = new double[width][height];
    }

    private FieldMap() {
    }

}
