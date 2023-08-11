package tankbase.gun;

import tankbase.ITank;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static tankbase.TankBase.TANK_SIZE;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MIN_BULLET_POWER;

public abstract class AbtractGunner implements Gunner {
    private ITank tank;
    private String name = this.getClass().getSimpleName();
    Map<String, FireStat> fireRoundStats = new HashMap<>();

    public AbtractGunner(ITank tank){
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
    public Color getColor() { return Color.BLACK; }

    public double getFirePower(ITank target) {
        //if (target.getGpBase().isDefenseFire()) return MIN_BULLET_POWER;
        //if (isEasyShot(target)) return MAX_BULLET_POWER;

        //double d = Math.max(TANK_SIZE*3, Math.min(TANK_SIZE*10, target.getGpBase().getCurrentPoint().distance(target)));
        //double power = range(d, TANK_SIZE*3, TANK_SIZE*10, MAX_BULLET_POWER, MIN_BULLET_POWER);
        double power = MAX_BULLET_POWER;

        // Apply a hitrate factor
        //power *= Math.pow(getEnemyRoundFireStat(target).getHitRate()+.5, 8);

        // Apply lastScan factor
        //power /= (1+target.getLastUpdateDelta());

        // Apply energy factor
        power *= tank.getEnergy()/100;
        power = Math.min(MAX_BULLET_POWER, Math.max(MIN_BULLET_POWER, power));

        if (target.getEnergy() == 0)
            power = MIN_BULLET_POWER;

        return power;
    }

    public boolean isEasyShot(ITank enemy) {
        //if (enemy.getLastUpdateDelta()>2) return false;

        return enemy.getEnergy() == 0 || enemy.getPosition().distance(tank.getPosition()) < TANK_SIZE*2;
               // || (enemy.getAccel() == 0 && enemy.getVelocity() == 0 );
    }

    @Override
    public ITank getTank(){
        return tank;
    }
}
