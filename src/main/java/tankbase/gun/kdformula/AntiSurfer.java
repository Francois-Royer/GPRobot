package tankbase.gun.kdformula;

import robocode.Rules;
import tankbase.ITank;
import tankbase.Move;
import tankbase.TankState;
import tankbase.enemy.Enemy;
import tankbase.gun.Fire;
import tankbase.kdtree.KdTree;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.AbstractTankBase.FIELD_WIDTH;
import static tankbase.Constant.TANK_SIZE;
import static tankbase.TankUtils.concatArray;
import static tankbase.TankUtils.getPointAngle;
import static tankbase.TankUtils.pointInBattleField;
import static tankbase.enemy.Enemy.MAX_GUN_HEAT;
import static tankbase.enemy.EnemyDB.countFilteredEnemies;
import static tankbase.enemy.EnemyDB.enemyCount;
import static tankbase.gun.log.FireLog.getFireLog;

public class AntiSurfer extends AbastractKDFormula {
    double[] weights = {3, 4, 3, 2, 2, 4, 2, 3, 2, 2};
    ITank target;
    ITank firer;

    public AntiSurfer(ITank target, ITank firer) {
        this.target = target;
        this.firer = firer;
        kdTree = new KdTree.WeightedSqrEuclid<>(weights.length, 400);
        kdTree.setWeights(weights);
    }

    public double[] getPoint() {
        TankState state = target.getState();
        List<Fire> aimLog = getFireLog(target.getName());
        if (aimLog.isEmpty()) { return null;}
        Fire f = aimLog.get(0);
        double targetDistance = f.distance(state);
        double targetRelativeHeading = state.getHeadingRadians() - firer.getState().getGunHeadingRadians();
        double wallDistance = directToWallDistance(state, state.getHeadingRadians());
        double wallRevDistance = directToWallDistance(state, state.getHeadingRadians()+PI);
        double aliveCount = countFilteredEnemies(Enemy::isAlive);

        return new double[] {
                min(91, f.distance(state) / f.getVelocity()) / 91,
                ((state.getVelocity()) + 0.1) / 8.1,
                (cos(targetRelativeHeading) + 1) / 2,
                sin(targetRelativeHeading),
                abs(state.getAcceleration()) / 2,
                wallDistance/DISTANCE_MAX,
                wallRevDistance/DISTANCE_MAX,
                min(1.0, ((double) target.getLastVelocityChange())
                        / targetDistance / f.getVelocity()),
                firer.getState().getGunHeat() / MAX_GUN_HEAT,
                aliveCount>1 ? sqrt((aliveCount - 1) / max(enemyCount() - 1,1)) : 0
        };
    }

    public double directToWallDistance(Point2D.Double target, double heading) {
        double cosHeading = cos(heading);
        double sinHeading = sin(heading);

        Point2D.Double d = new Point2D.Double(target.x, target.y);

        for (int x = 0; pointInBattleField(d, TANK_SIZE/2); x++) {
            d.x += cosHeading;
            d.y += sinHeading;
        }

        return d.distance(target);
    }
}
