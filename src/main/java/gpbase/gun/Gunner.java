package gpbase.gun;

import gpbase.Enemy;
import robocode.Bullet;

public interface Gunner {
    String getName();
    AimingData aim(Enemy enemy);

    // Stats
    void fire(Enemy enemy);
    void hit(Enemy enemy);
    double hitRate(Enemy enemy);
    void resetStat(Enemy enemy);
    double hitRate();
    void resetStat();
}
