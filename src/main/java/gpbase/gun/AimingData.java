package gpbase.gun;

import gpbase.Enemy;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static robocode.Rules.MIN_BULLET_POWER;

public class AimingData {
    private Enemy target;
    private Point.Double firingPosition;
    private double firePower;
    private List<Point.Double> expectedMoves;
    private double confidence;

    public AimingData(Enemy target) {
        this.target = target;
        this.firingPosition = target;
        this.firePower = MIN_BULLET_POWER;
        this.expectedMoves = new ArrayList<>();
        this.confidence = 0;
    }

    public AimingData(Enemy target, Point.Double firingPosition, double firePower, List<Point.Double> expectedMoves, double confidence) {
        this.target = target;
        this.firingPosition = firingPosition;
        this.firePower = firePower;
        this.expectedMoves = expectedMoves;
        this.confidence = confidence;
    }

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
}
