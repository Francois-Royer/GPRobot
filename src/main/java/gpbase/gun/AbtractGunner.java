package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static gpbase.GPUtils.*;
import static java.lang.Math.*;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MIN_BULLET_POWER;

public abstract class AbtractGunner implements Gunner {

    private double  dmax = 400;

    private String name = this.getClass().getSimpleName();
    Map<String, FireStat> fireStats = new HashMap<>();
    FireStat globalStat = new FireStat();

    boolean resetStat = false;

    @Override
    public abstract AimingData aim(Enemy enemy);

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void fire(Enemy enemy) {
        globalStat.fire();
        getFireStat(enemy).fire();

    }

    @Override
    public void hit(Enemy enemy) {
        globalStat.hit();
        getFireStat(enemy).hit();
    }

    @Override
    public double hitRate(Enemy enemy) {
        return getFireStat(enemy).getHitRate();
    }

    @Override
    public long fireCount(Enemy enemy) {
        return getFireStat(enemy).getFireCount();
    }

    @Override
    public long hitCount(Enemy enemy) {
        return getFireStat(enemy).getHitCount();
    }

    @Override
    public void resetStat(Enemy enemy) {
        if (resetStat) getFireStat(enemy).reset();
    }

    @Override
    public double hitRate() { return globalStat.getHitRate(); }

    @Override
    public long hitCount() { return globalStat.getHitCount(); }

    @Override
    public long fireCount() { return globalStat.getFireCount(); }

    @Override
    public void resetStat() {
        if (resetStat) globalStat.reset();
    }

    private FireStat getFireStat(Enemy enemy) {
        FireStat fireStat = fireStats.get(enemy.getName());

        if (fireStat == null)
            fireStats.put(enemy.getName(), fireStat = new FireStat(0,0));

        return fireStat;
    }

    public void setResetStat(boolean resetStat) {
        this.resetStat = resetStat;
    }

    public double getFirePower(Enemy enemy) {
        if (isEasyShot(enemy)) return MAX_BULLET_POWER;
        double d = Math.max(GPBase.TANK_SIZE*2, Math.min(dmax, enemy.getGpBase().getCurrentPoint().distance(enemy)));
        //double power = range((hitRate(enemy) + 2*d)/3, 0, 1, MIN_BULLET_POWER, MAX_BULLET_POWER);
        double power = range(d, GPBase.TANK_SIZE*2, dmax, MAX_BULLET_POWER, MIN_BULLET_POWER);

        // Apply a hitrate factor
        power *= Math.pow(hitRate(enemy)+.5, 8);

        // Apply energy factor
        power *= (enemy.getGpBase().getEnergy() + 50 )/100;
        power = Math.min(MAX_BULLET_POWER, Math.max(MIN_BULLET_POWER, power));


        if (power < MIN_BULLET_POWER) {
            enemy.getGpBase().out.println("power < MIN_BULLET_POWER");
            enemy.getGpBase().out.printf("hitRate=%f\n", hitRate(enemy));
        }

        return power;
    }

    public boolean isEasyShot(Enemy enemy) {
        return (enemy.getEnergy() == 0) || (enemy.distance(enemy.getGpBase().getCurrentPoint()) < GPBase.TANK_SIZE*4);
    }

}
