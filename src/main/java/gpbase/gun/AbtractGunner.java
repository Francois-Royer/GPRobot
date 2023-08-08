package gpbase.gun;

import gpbase.Enemy;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static gpbase.GPBase.TANK_SIZE;
import static gpbase.GPUtils.range;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MIN_BULLET_POWER;

public abstract class AbtractGunner implements Gunner {

    private double  dmax = 400;

    private String name = this.getClass().getSimpleName();
    Map<String, FireStat> fireRoundStats = new HashMap<>();

    @Override
    public abstract AimingData aim(Enemy enemy);

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FireStat getEnemyRoundFireStat(Enemy enemy) {
        FireStat fireStat = fireRoundStats.get(enemy.getName());

        if (fireStat == null)
            fireRoundStats.put(enemy.getName(), fireStat = new FireStat());

        return fireStat;
    }

    @Override
    public void resetRoundStat() {
        fireRoundStats.clear();
    }

    @Override
    public Color getColor() { return Color.BLACK; }

    public double getFirePower(Enemy enemy) {
        if (enemy.getGpBase().isDefenseFire()) return MIN_BULLET_POWER;
        if (isEasyShot(enemy)) return MAX_BULLET_POWER;

        //double d = Math.max(TANK_SIZE*3, Math.min(TANK_SIZE*10, enemy.getGpBase().getCurrentPoint().distance(enemy)));
        //double power = range(d, TANK_SIZE*3, TANK_SIZE*10, MAX_BULLET_POWER, MIN_BULLET_POWER);
        double power = MAX_BULLET_POWER;

        // Apply a hitrate factor
        //power *= Math.pow(getEnemyRoundFireStat(enemy).getHitRate()+.5, 8);

        // Apply lastScan factor
        //power /= (1+enemy.getLastUpdateDelta());

        // Apply energy factor
        power *= enemy.getGpBase().getEnergy()/100;

        power = Math.min(MAX_BULLET_POWER, Math.max(MIN_BULLET_POWER, power));

        return power;
    }

    public boolean isEasyShot(Enemy enemy) {
        if (enemy.getLastUpdateDelta()>2) return false;

        return enemy.getEnergy() == 0 || enemy.distance(enemy.getGpBase().getCurrentPoint()) < TANK_SIZE*2;
               // || (enemy.getAccel() == 0 && enemy.getVelocity() == 0 );
    }
}
