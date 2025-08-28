package tankbase.gun;

import tankbase.ITank;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.*;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MIN_BULLET_POWER;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.Constant.TANK_SIZE;

public abstract class AbtractGunner implements Gunner {
    private final String name = this.getClass().getSimpleName();
    Map<String, FireStat> fireRoundStats = new HashMap<>();
    private ITank gunner;

    protected AbtractGunner(ITank gunner) {
        this.gunner = gunner;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FireStat getEnemyRoundFireStat(ITank target) {
        return fireRoundStats.computeIfAbsent(target.getName(), k -> new FireStat());
    }

    @Override
    public void resetRoundStat() {
        fireRoundStats.clear();
    }

    @Override
    public Color getColor() {
        return Color.PINK;
    }

    public double getFirePower(ITank target) {
        if (target.getState().getEnergy() == 0)
            return MIN_BULLET_POWER;

        double power = MAX_BULLET_POWER;
        double close = 5 * TANK_SIZE;
        double distance = target.getState().distance(gunner.getState());

        // Apply distance factor
        if (distance > close)
            power *= pow(1 - (distance - close) / DISTANCE_MAX, 2);

        // Apply a hitrate factor
        power *= pow(getEnemyRoundFireStat(target).getHitRate() + .5, 4);

        // Apply lastScan factor
        power /= 1 + (target.getState().getTime() - target.getLastScan()) / 5.0;

        // shot for remaining energie
        power = min(power, getBulletPowerForDamage(target.getFEnergy() + 1));

        // check min/max
        power = min(MAX_BULLET_POWER, max(MIN_BULLET_POWER, power));

        // enemy with 0 energy should be shoot asap for kill bonus and avoid it regain energy
        if (target.getState().getEnergy() <= 0)
            power = MIN_BULLET_POWER;

        return power;
    }

    double getBulletPowerForDamage(double damage) {
        if (damage < 4)
            return damage / 4;
        return (damage + 2) / 6;
    }

    @Override
    public ITank getGunner() {
        return gunner;
    }

    @Override
    public void setGunner(ITank gunner) {
        this.gunner = gunner;
    }
}
