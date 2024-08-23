package tankbase.gun;

import tankbase.ITank;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MIN_BULLET_POWER;
import static tankbase.TankBase.DISTANCE_MAX;
import static tankbase.TankBase.TANK_SIZE;

public abstract class AbtractGunner implements Gunner {
    private final ITank tank;
    private final String name = this.getClass().getSimpleName();
    Map<String, FireStat> fireRoundStats = new HashMap<>();

    public AbtractGunner(ITank tank) {
        this.tank = tank;
    }

    @Override
    public abstract AimingData aim(ITank target);

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FireStat getEnemyRoundFireStat(ITank target) {
        FireStat fireStat = fireRoundStats.get(target.getName());

        if (fireStat == null)
            fireRoundStats.put(target.getName(), fireStat = new FireStat());

        return fireStat;
    }

    @Override
    public void resetRoundStat() {
        fireRoundStats.clear();
    }

    @Override
    public Color getColor() {
        return Color.BLACK;
    }

    public double getFirePower(ITank target) {
        if (target.getEnergy() == 0)
            return MIN_BULLET_POWER;

        double power = MAX_BULLET_POWER;
        double close = 5 * TANK_SIZE;
        double distance = target.getPosition().distance(tank.getPosition());

        // Apply distance factor
        if (distance > close)
            power *= 1 - distance / DISTANCE_MAX / 6;

        // Apply a hitrate factor
        power *= Math.pow(getEnemyRoundFireStat(target).getHitRate() + .5, 6);

        // Apply lastScan factor
        //power /= (1+target.getLastUpdateDelta());

        // Apply energy factor
        power *= tank.getEnergy() / 100;

        // shot for remaining energie
        power = min(power, getBulletPowerForDamage(target.getFEnergy() + 1));

        // check min/max
        power = min(MAX_BULLET_POWER, max(MIN_BULLET_POWER, power));

        // enemy with 0 energy should be shoot asap for kill bonus and avoid it regain energy
        if (target.getFEnergy() <= 0)
            power = MIN_BULLET_POWER;

        return power;
    }

    double getBulletPowerForDamage(double damage) {
        if (damage < 4)
            return damage / 4;
        return (damage + 2) / 6;
    }

    @Override
    public ITank getTank() {
        return tank;
    }
}
