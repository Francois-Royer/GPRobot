package tankbase.gun.kdformula;

import tankbase.AbstractTankBase;
import tankbase.ITank;
import tankbase.TankState;
import tankbase.TankUtils;
import tankbase.enemy.Enemy;
import tankbase.gun.Fire;
import tankbase.kdtree.KdTree;

import java.util.List;

import static java.lang.Math.*;
import static robocode.Rules.*;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.TankUtils.pointInBattleField;
import static tankbase.enemy.Enemy.MAX_GUN_HEAT;
import static tankbase.enemy.EnemyDB.countFilteredEnemies;
import static tankbase.enemy.EnemyDB.enemyCount;
import static tankbase.gun.log.FireLog.getFireLog;

public class AntiSurfer extends AbstractKDFormula {
    double MAX_BULLET_SPEED = getBulletSpeed(MAX_BULLET_POWER);
    double[] weights = {1,1,1,1,5,5,1,1,10,10,10,2,2,2,1,1,1};
    ITank target;
    ITank firer;

    public AntiSurfer(ITank target, AbstractTankBase base) {
        super(base);
        this.target = target;
        this.firer = base;
        kdTree = new KdTree.WeightedSqrEuclid<>(weights.length, 256);
        kdTree.setWeights(weights);
    }

    public double[] getPoint() {
        TankState state = target.getState();
        List<Fire> aimLog = getFireLog(target.getName());
        if (aimLog.isEmpty()) {
            return null;
        }
        double wallDistance = TankUtils.directToWallDistance(state, state.getHeadingRadians());
        double wallRevDistance = TankUtils.directToWallDistance(state, state.getHeadingRadians() + PI);
        double aliveCount = countFilteredEnemies(Enemy::isAlive);

        return new double[]{
                state.getVelocity() / MAX_VELOCITY,
                state.getAcceleration() / 2,
                wallDistance,
                wallRevDistance,
                min(1.0, ((double) target.getLastStop())/100),
                min(1.0, ((double) target.getLastVelocityChange())/100),
                firer.getState().getGunHeat() / MAX_GUN_HEAT,
                aliveCount > 1 ? sqrt((aliveCount - 1) / max(enemyCount() - 1, 1)) : 0,

                base.bulletDistance(0)/DISTANCE_MAX*base.bulletSpeed(0)/MAX_BULLET_SPEED,
                base.bulletPower(0)/ MAX_BULLET_POWER,
                base.bulletRelativeAngle(0),

                base.bulletDistance(1)/DISTANCE_MAX*base.bulletSpeed(1)/MAX_BULLET_SPEED,
                base.bulletPower(1)/ MAX_BULLET_POWER,
                base.bulletRelativeAngle(1),

                base.bulletDistance(2)/DISTANCE_MAX*base.bulletSpeed(2)/MAX_BULLET_SPEED,
                base.bulletPower(2)/ MAX_BULLET_POWER,
                base.bulletRelativeAngle(2)
        };
    }
}
