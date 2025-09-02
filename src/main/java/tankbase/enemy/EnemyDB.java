package tankbase.enemy;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.function.Predicate;

public class EnemyDB {
    private static Map<String, Enemy> enemies = new HashMap<>();

    private EnemyDB() {
    }

    public static Collection<Enemy> listAllEnemies() {
        return Collections.unmodifiableCollection(enemies.values());
    }

    public static List<Enemy> filterEnemies(Predicate<Enemy> filter) {
        return enemies.values().stream().filter(filter).toList();
    }

    public static long countFilteredEnemies(Predicate<Enemy> filter) {
        return enemies.values().stream().filter(filter).count();
    }

    public static long enemyCount() {
        return enemies.size();
    }

    public static List<Enemy> filterAndSortEnemies(Predicate<Enemy> filter, Comparator<Enemy> comparator) {
        return enemies.values().stream().filter(filter).sorted(comparator).toList();
    }

    public static Enemy getEnemy(String name) {
        return enemies.get(name);
    }

    public static Enemy getCloseScannedEnemy(Point2D.Double point) {
        Comparator<Enemy> comp = new CloseEnemy(point);

        return enemies.values().stream()
                .filter(Enemy::isAlive)
                .filter(Enemy::isScanned)
                .filter(e -> e.getState() != null)
                .sorted(comp)
                .findFirst().orElse(null);
    }

    public static Enemy getCloseAliveEnemy(Point2D.Double point) {
        Comparator<Enemy> comp = new CloseEnemy(point);

        return enemies.values().stream()
                .filter(Enemy::isAlive)
                .filter(e -> e.getState() != null)
                .sorted(comp)
                .findFirst().orElse(null);
    }

    public static Enemy addEnemy(Enemy enemy) {
        return enemies.put(enemy.getName(), enemy);
    }

    static class CloseEnemy implements Comparator<Enemy> {
        private final Point2D.Double point;

        public CloseEnemy(Point2D.Double point) {
            this.point = point;
        }

        @Override
        public int compare(Enemy e1, Enemy e2) {
            return Double.compare(point.distance(e1.getState()),
                    point.distance(e2.getState()));
        }
    }

    ;
}
