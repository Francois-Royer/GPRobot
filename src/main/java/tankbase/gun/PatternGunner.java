package tankbase.gun;

import tankbase.Enemy;
import tankbase.ITank;
import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.util.List;

import static tankbase.TankBase.pointInBattleField;
import static java.lang.Math.abs;
import static tankbase.TankUtils.getPatternPoint;

public class PatternGunner extends AbstractKdTreeGunner {

    public PatternGunner(ITank tank) {
        super(tank);
    }

    @Override
    public AimingData aim(ITank target) {
        if (target.getPatternKdTree() == null) return null;

        double[] kdPoint = getPatternPoint(target);
        List<KdTree.Entry<List<Move>>> el = target.getPatternKdTree().nearestNeighbor(kdPoint, 10, true);

        return getKdTreeAimingData(target, el);
    }

    @Override
    public Color getColor() { return Color.RED; }

}
