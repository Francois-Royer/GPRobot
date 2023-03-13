package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;

import java.util.HashMap;
import java.util.Map;

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

    public double getFirePower(Enemy enemy) {
        if (isEasyShot(enemy)) return MAX_BULLET_POWER;
        double d = Math.max(GPBase.TANK_SIZE*2, Math.min(dmax, enemy.getGpBase().getCurrentPoint().distance(enemy)));
        //double power = range((hitRate(enemy) + 2*d)/3, 0, 1, MIN_BULLET_POWER, MAX_BULLET_POWER);
        double power = range(d, GPBase.TANK_SIZE*3, dmax, MAX_BULLET_POWER, MIN_BULLET_POWER);

        // Apply a hitrate factor
        power *= Math.pow(getEnemyRoundFireStat(enemy).getHitRate()+.5, 4);

        // Apply energy factor
        //power *= (enemy.getGpBase().getEnergy() + 50 )/100;
        power = Math.min(MAX_BULLET_POWER, Math.max(MIN_BULLET_POWER, power));


        if (power < MIN_BULLET_POWER) {
            enemy.getGpBase().out.println("power < MIN_BULLET_POWER");
            enemy.getGpBase().out.printf("hitRate=%f\n", getEnemyRoundFireStat(enemy).getHitRate());
        }

        return power;
    }

    public boolean isEasyShot(Enemy enemy) {
        return (enemy.getEnergy() == 0) || (enemy.distance(enemy.getGpBase().getCurrentPoint()) < GPBase.TANK_SIZE*4);
    }

}
