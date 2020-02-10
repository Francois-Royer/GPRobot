package gpbase.gun;

import gpbase.Enemy;
import gpbase.GPUtils;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class AimingData {
    private Gunner gunner;
    private Enemy target;
    private Point.Double firingPosition;
    private Double angle;
    private double firePower;
    private List<Point.Double> expectedMoves;
    private double confidence;

    private double[] kdPoint;

    public AimingData(Gunner gunner, Enemy target, double firePower, double confidence) {
        this(gunner, target, target, firePower, new ArrayList<>(), confidence);
    }

    public AimingData(Gunner gunner, Enemy target, Point.Double firingPosition, double firePower, List<Point.Double> expectedMoves, double confidence) {
        this(gunner, target, firingPosition, firePower, expectedMoves, confidence, null);
    }

    public AimingData(Gunner gunner, Enemy target, Point.Double firingPosition, double firePower, List<Point.Double> expectedMoves, double confidence, double[] kdPoint) {
        this.gunner = gunner;
        this.target = target;
        this.firingPosition = firingPosition;
        this.firePower = firePower;
        this.expectedMoves = expectedMoves;
        this.confidence = confidence;
        this.angle = GPUtils.getAngle(target.getGpBase().getCurrentPoint(), firingPosition);
        this.kdPoint = kdPoint;
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

    public double[] getKdPoint() {
        return kdPoint;
    }
}
