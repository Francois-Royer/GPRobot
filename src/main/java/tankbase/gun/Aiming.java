package tankbase.gun;

import tankbase.ITank;
import tankbase.Move;
import tankbase.TankUtils;
import tankbase.kdtree.KdTree;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Aiming {
    private final Gunner gunner;
    private final ITank target;
    private final Point2D.Double nextPosition;
    private final double firePower;
    private final List<Point2D.Double> expectedMoves;
    private final KdTree.Entry<List<Move>> kdEntry;
    private Point2D.Double firingPosition;
    private Double direction;

    public Aiming(Gunner gunner, ITank target, double firePower) {
        this(gunner, target, target.getState().getPosition(), firePower);
    }

    public Aiming(Gunner gunner, ITank target, Point2D.Double firingPosition, double firePower) {
        this(gunner, target, firingPosition, firingPosition, firePower, new ArrayList<>());
    }

    public Aiming(Gunner gunner, ITank target, Point2D.Double firingPosition, Point2D.Double nextPosition, double firePower,
                  List<Point2D.Double> expectedMoves) {
        this(gunner, target, firingPosition, nextPosition, firePower, expectedMoves, null);
    }

    public Aiming(Gunner gunner, ITank target, Point2D.Double firingPosition, Point2D.Double nextPosition, double firePower,
                  List<Point2D.Double> expectedMoves, KdTree.Entry<List<Move>> kdEntry) {
        this.gunner = gunner;
        this.target = target;
        this.firingPosition = firingPosition;
        this.nextPosition = nextPosition;
        this.firePower = firePower;
        this.expectedMoves = expectedMoves;
        this.direction = TankUtils.getPointAngle(gunner.getGunner().getState().getPosition(), firingPosition);
        this.kdEntry = kdEntry;
    }

    public Gunner getGunner() {
        return gunner;
    }

    public ITank getTarget() {
        return target;
    }

    public Point2D.Double getFiringPosition() {
        return firingPosition;
    }

    public void setFiringPosition(Point2D.Double firingPosition) {
        this.firingPosition = firingPosition;
    }

    public Point2D.Double getNextPosition() {
        return nextPosition;
    }

    public double getFirePower() {
        return firePower;
    }

    public List<Point2D.Double> getExpectedMoves() {
        return expectedMoves;
    }

    public Double getDirection() {
        return direction;
    }

    public void setDirection(Double direction) {
        this.direction = direction;
    }

    public KdTree.Entry<List<Move>> getKdEntry() {
        return kdEntry;
    }

    public double hitRate() {
        return gunner.getEnemyRoundFireStat(target).getHitRate();
    }

    public Aiming copy() {
        return new Aiming(gunner, target, firingPosition, nextPosition, firePower, expectedMoves, kdEntry);
    }


    @Override
    public String toString() {
        return String.format("Aiming[target=%s, firePower=%.2f, direction=%.2f, firing to=(%.2f, %.2f) from(%.2f, %.2f)]",
                             target.getName(), firePower, Math.toDegrees(direction), firingPosition.x, firingPosition.y,
                             gunner.getGunner().getState().getPosition().x, gunner.getGunner().getState().getPosition().y);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Aiming aiming = (Aiming) o;
        return Double.compare(firePower, aiming.firePower) == 0 && Objects.equals(nextPosition,
                                                                                  aiming.nextPosition) && Objects.equals(
                expectedMoves, aiming.expectedMoves) && Objects.equals(kdEntry, aiming.kdEntry) && firingPosition.equals(
                aiming.firingPosition) && direction.equals(aiming.direction);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(nextPosition);
        result = 31 * result + Double.hashCode(firePower);
        result = 31 * result + Objects.hashCode(expectedMoves);
        result = 31 * result + Objects.hashCode(kdEntry);
        result = 31 * result + firingPosition.hashCode();
        result = 31 * result + direction.hashCode();
        return result;
    }
}
