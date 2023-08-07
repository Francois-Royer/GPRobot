package gpbase.gun;

import gpbase.Enemy;
import gpbase.Move;
import gpbase.kdtree.KdTree;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static gpbase.GPBase.TANK_SIZE;
import static gpbase.GPBase.pointInBattleField;
import static gpbase.GPUtils.clonePoint;
import static java.lang.Math.*;
import static java.lang.Math.abs;
import static robocode.Rules.MIN_BULLET_POWER;
import static robocode.Rules.getBulletSpeed;

public class PatternGunner extends AbstractKdTreeGunner {

    @Override
    public AimingData aim(Enemy enemy) {
        if (enemy.getPatternKdTree() == null) return null;

        double[] kdPoint = enemy.getPatternPoint();
        double firePower = getFirePower(enemy)*2;
        List<KdTree.Entry<List<Move>>> el = enemy.getPatternKdTree().nearestNeighbor(kdPoint, 10, true);

        return getKdTreeAimingData(enemy, firePower, el);
    }

    @Override
    public Color getColor() { return Color.RED; }

}
