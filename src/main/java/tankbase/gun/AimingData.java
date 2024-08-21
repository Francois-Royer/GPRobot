package tankbase.gun;

import tankbase.ITank;
import tankbase.TankUtils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class AimingData {
    private Gunner gunner;
    private ITank target;
    private Point.Double firingPosition;
    private Point.Double nextPosition;
    private Double direction;
    private double firePower;
    private List<Point.Double> expectedMoves;

    private double[] kdPoint;

    public AimingData(Gunner gunner, ITank target, double firePower) {
        this(gunner, target, target.getPosition(), firePower);
    }

    public AimingData(Gunner gunner, ITank target, Point.Double firingPosition, double firePower) {
        this(gunner, target, firingPosition, firingPosition, firePower, new ArrayList<>());
    }

    public AimingData(Gunner gunner, ITank target, Point.Double firingPosition, Point2D.Double nextPosition, double firePower, List<Point.Double> expectedMoves) {
        this(gunner, target, firingPosition, nextPosition, firePower, expectedMoves, null);
    }

    public AimingData(Gunner gunner, ITank target, Point.Double firingPosition, Point2D.Double nextPosition, double firePower, List<Point.Double> expectedMoves, double[] kdPoint) {
        this.gunner = gunner;
        this.target = target;
        this.firingPosition = firingPosition;
        this.nextPosition = nextPosition;
        this.firePower = firePower;
        this.expectedMoves = expectedMoves;
        this.direction = TankUtils.getPointAngle(gunner.getTank().getPosition(), firingPosition);
        this.kdPoint = kdPoint;
    }

    public Gunner getGunner() { return gunner; }

    public ITank getTarget() {
        return target;
    }

    public Point.Double getFiringPosition() {
        return firingPosition;
    }

    public Point.Double getNextPosition() {
        return nextPosition;
    }

    public double getFirePower() {
        return firePower;
    }

    public List<Point.Double> getExpectedMoves() {
        return expectedMoves;
    }


    public Double getDirection() { return direction; }

    public void setDirection(Double direction) {
        this.direction = direction;
    }

    public void setFiringPosition(Point.Double firingPosition) {
        this.firingPosition = firingPosition;
    }

    public double[] getKdPoint() {
        return kdPoint;
    }

    public double hitRate() { return gunner.getEnemyRoundFireStat(target).getHitRate() ;}

    public AimingData copy() {
        return new AimingData(gunner, target, firingPosition, nextPosition,  firePower, expectedMoves, kdPoint);
    }
}
