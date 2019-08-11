package gpbase.gun;

import gpbase.Enemy;

import java.util.HashMap;
import java.util.Map;

public abstract class AbtractGunner implements Gunner {
    private String name = this.getName();
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
            fireStats.put(enemy.getName(), fireStat = new FireStat());

        return fireStat;
    }
}
