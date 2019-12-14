package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPBase;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static gpbase.GPUtils.getAngle;
import static gpbase.GPUtils.trigoAngle;
import static java.lang.Math.PI;
import static java.lang.Math.abs;

public abstract class AbtractGunner implements Gunner {
    private String name = this.getClass().getSimpleName();
    Map<String, FireStat> fireStats = new HashMap<>();
    FireStat globalStat = new FireStat();

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
    public void cancelFire(Enemy enemy) {
        globalStat.cancelFire();
        getFireStat(enemy).cancelFire();

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
    public double hitRate() {
        return globalStat.getHitRate();
    }

    private FireStat getFireStat(Enemy enemy) {
        FireStat fireStat = fireStats.get(enemy.getName());

        if (fireStat == null)
            fireStats.put(enemy.getName(), fireStat = new FireStat(1,1));

        return fireStat;
    }
}
