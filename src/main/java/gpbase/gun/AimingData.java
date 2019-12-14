package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPUtils;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static robocode.Rules.MIN_BULLET_POWER;

public class AimingData {
    private Gunner gunner;
    private Enemy target;
    private Point.Double firingPosition;
    private Double angle;
    private double firePower;
    private List<Point.Double> expectedMoves;
    private double confidence;

    public AimingData(Gunner gunner, Enemy target, double firePower, double confidence) {
        this(gunner, target, target, firePower, new ArrayList<>(), confidence);
    }

    public AimingData(Gunner gunner, Enemy target, Point.Double firingPosition, double firePower, List<Point.Double> expectedMoves, double confidence) {
        this.gunner = gunner;
        this.target = target;
        this.firingPosition = firingPosition;
        this.firePower = firePower;
        this.expectedMoves = expectedMoves;
        this.confidence = confidence;
        this.angle = GPUtils.getAngle(target.getGpBase().getCurrentPoint(), firingPosition);
    }

    public Gunner getGunner() { return gunner; }

    public Enemy getTarget() {
        return target;
    }

    public Point.Double getFiringPosition() {
        return firingPosition;
    }

    public double getFirePower() {
        return firePower;
    }

    public List<Point.Double> getExpectedMoves() {
        return expectedMoves;
    }

    public double getConfidence() {
        return confidence;
    }

    public Double getAngle() { return angle; }

    public void setAngle(Double angle) {
        this.angle = angle;
    }
}
