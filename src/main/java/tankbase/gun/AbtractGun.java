package tankbase.gun;

import tankbase.ITank;
import tankbase.wave.Wave;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MIN_BULLET_POWER;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.Constant.TANK_SIZE;
import static tankbase.wave.WaveLog.getWaves;

public abstract class AbtractGun implements Gun {
    private final String name = this.getClass().getSimpleName();
    Map<String, FireStat> fireRoundStats = new HashMap<>();
    private ITank firer;

    protected AbtractGun(ITank firer) {
        this.firer = firer;
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

        Optional<Wave> ow = getWaves().stream().filter(w -> w.getSource().getName().equals(target.getName()))
                .sorted((w1, w2) -> Long.compare(w1.getStart(), w2.getStart())).limit(1).findFirst();

        if (ow.isPresent()) {
            return ow.get().getPower();
        }

        double power = MAX_BULLET_POWER;
        double close = 3 * TANK_SIZE;
        double distance = target.getState().distance(firer.getState());

        // Apply distance factor
        power *= pow(1 - (distance - close) / DISTANCE_MAX, 4);

        // Apply a hitrate factor
        power *= pow(getEnemyRoundFireStat(target).getHitRate() + .5, 4);

        // Apply lastScan factor
        power /= 1 + (target.getState().getTime() - target.getLastScan()) / 5.0;

        // shot for remaining energie
        power = min(power, getBulletPowerForDamage(target.getFEnergy()) + .1);

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
    public ITank getFirer() {
        return firer;
    }

    @Override
    public void setFirer(ITank firer) {
        this.firer = firer;
    }
}
