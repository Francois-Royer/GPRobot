package gpbase.gun;

import gpbase.Enemy;

public interface Gunner {
    String getName();
    AimingData aim(Enemy enemy);
    public double getFirePower(Enemy enemy);

    FireStat getEnemyRoundFireStat(Enemy enemy);
    void resetRoundStat();
}
