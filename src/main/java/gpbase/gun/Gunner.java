package gpbase.gun;

import gpbase.Enemy;

public interface Gunner {
    String getName();
    AimingData aim(Enemy enemy);

    // Stats
    void fire(Enemy enemy);
    void hit(Enemy enemy);
    double hitRate(Enemy enemy);
    double hitRate();
}
