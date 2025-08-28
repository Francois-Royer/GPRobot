package tankbase.gun.kdformula;

import robocode.Rules;
import tankbase.ITank;
import tankbase.TankState;
import tankbase.gun.Fire;
import tankbase.kdtree.KdTree;

import java.util.List;

import static java.lang.Math.PI;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.enemy.Enemy.MAX_GUN_HEAT;
import static tankbase.gun.log.FireLog.getFireLog;
import static tankbase.TankUtils.concatArray;
import static tankbase.TankUtils.getPointAngle;

public class Surfer extends Pattern {
    private static final double[] surferWeights = {1, 2, 10, 10, 10};
    private final ITank gunner;

    public Surfer(ITank target, ITank gunner) {
        super(target);
        this.gunner = gunner;
        weights = concatArray(super.weights, surferWeights);
        kdTree = new KdTree.WeightedSqrEuclid<>(weights.length, KDTREE_MAX_SIZE);
        kdTree.setWeights(weights);
    }

    @Override
    public double[] getPoint(TankState state) {
        double[] surferPoint = {
                state.distance(gunner.getState()) / DISTANCE_MAX,
                normalRelativeAngle(state.getHeadingRadians() - getPointAngle(gunner.getState(), state)) / PI,
                gunner.getState().getGunHeat() / MAX_GUN_HEAT,
                0, 0
        };

        List<Fire> aimLog = getFireLog(target.getName());
        if (!aimLog.isEmpty()) {
            surferPoint[3] = aimLog.get(0).age(state.getTime()) * Rules.getBulletSpeed(
                    aimLog.get(0).getAimingData().getFirePower()) / DISTANCE_MAX;
            surferPoint[4] = aimLog.get(0).getAimingData().getFirePower() / MAX_BULLET_POWER;
        }

        /*for (int i = 0; i < aimLog.size() && i * 2 + 4 < surferPoint.length; i++) {
            surferPoint[i * 2 + 3] = aimLog.get(i).getAimingData().getFirePower() / MAX_BULLET_POWER;
            surferPoint[i * 2 + 4] = aimLog.get(i).age(gunner.getState().getDate()) / DISTANCE_MAX * Rules.getBulletSpeed(aimLog.get(i).getAimingData().getFirePower());
        }*/

        return concatArray(super.getPoint(state), surferPoint);
    }
}
