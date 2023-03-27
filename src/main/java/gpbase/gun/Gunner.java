package gpbase.gun;

import gpbase.Enemy;

import java.awt.*;

public interface Gunner {
    String getName();
    AimingData aim(Enemy enemy);
    public double getFirePower(Enemy enemy);

    FireStat getEnemyRoundFireStat(Enemy enemy);
    void resetRoundStat();

    Color getColor();
}
