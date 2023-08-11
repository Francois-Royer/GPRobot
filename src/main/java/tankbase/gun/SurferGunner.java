package tankbase.gun;

import tankbase.Enemy;
import tankbase.ITank;
import tankbase.Move;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.util.List;

import static tankbase.TankUtils.getSurferPoint;

public class SurferGunner extends AbstractKdTreeGunner {

    public SurferGunner(ITank tank) {
        super(tank);
    }

    @Override
    public AimingData aim(ITank target) {
        if (target.getPatternKdTree() == null) return null;

        double[] kdPoint = getSurferPoint(target, getTank());
        double firePower = getFirePower(target)*2;
        List<KdTree.Entry<List<Move>>> el = target.getSurferKdTree().nearestNeighbor(kdPoint, 10, true);

        return getKdTreeAimingData(target, firePower, el);
    }

    @Override
    public Color getColor() { return Color.BLUE; }

}
