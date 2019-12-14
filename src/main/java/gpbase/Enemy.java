package gpbase;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import gpbase.gun.AimingData;
import robocode.*;
import gpbase.dataStructures.trees.KD.KdTree;

import static java.lang.Math.*;
import static gpbase.GPUtils.*;
import static robocode.util.Utils.*;
import static robocode.Rules.*;

public class Enemy extends Point.Double {
    double VARIANCE_SAMPLING = 10;
    String name;
    private double energy;
    double velocity;

    double direction; // enemy direction
    double angle; // angle from current pos to this enemy
    double rDirection; // direction - angle

    double accel = 0;
    double turn;
    double rotationRate = 0;
    double fEnergy;
    long lastUpdate; // Updated by movement prediction

    double vMax = 0;
    double vMin = 0;
    double vAvg = 0;
    double velocityVariance = 0;
    double turnVariance = 0;
    double velocityVarianceMax = 0;
    double turnVarianceMax = 0;

    long scanCount = 0;
    double scanDirection;
    double scanVelocity;
    long scanLastUpdate;
    long lastFire;

    Boolean alive = true;

    KdTree<List<Move>> kdtree = null;
    ArrayList<Move> moveLog = new ArrayList<>();

    GPBase gpBase;

    public Enemy(ScannedRobotEvent sre, GPBase gpBase) {
        name = sre.getName();
        this.gpBase = gpBase;
        update(sre);
    }

    public void update(ScannedRobotEvent sre) {
        long now = gpBase.getTime();
        scanCount++;

        if (scanCount > 1) {
            if (energy > sre.getEnergy() && this.lastFire + gpBase.FIRE_AGAIN_MIN_TIME < now) {
                double drop = energy - sre.getEnergy();
                if (drop >= MIN_BULLET_POWER && drop <= MAX_BULLET_POWER) {
                    double bspeed = getBulletSpeed(drop);
                    gpBase.waves.add(new Wave(name, bspeed, scanLastUpdate, this, gpBase));
                    this.lastFire = scanLastUpdate;
                }
            }
        }

        velocity = sre.getVelocity();
        direction = trigoAngle(sre.getHeadingRadians());
        angle = trigoAngle(gpBase.getHeadingRadians() + sre.getBearingRadians());
        rDirection = normalRelativeAngle(direction - angle);
        x = gpBase.getX() + sre.getDistance() * cos(angle);
        y = gpBase.getY() + sre.getDistance() * sin(angle);

        if (scanCount > 1) {
            double prevTurn = turn;
            turn = normalRelativeAngle(direction - scanDirection);

            // Compute variances
            velocityVariance = (abs(velocity - scanVelocity) / (now - scanLastUpdate) + velocityVariance * (VARIANCE_SAMPLING - 1)) / VARIANCE_SAMPLING;
            velocityVarianceMax = max(velocityVariance, velocityVarianceMax);
            turnVariance = (abs(turn - prevTurn) / (now - scanLastUpdate) + turnVariance * (VARIANCE_SAMPLING - 1)) / VARIANCE_SAMPLING;
            turnVarianceMax = max(turnVariance, turnVarianceMax);

            rotationRate = turn / (now - scanLastUpdate);
            this.rotationRate = checkMinMax(this.rotationRate, -MAX_TURN_RATE_RADIANS, MAX_TURN_RATE_RADIANS);
            boolean isDecelerate = abs(velocity) < abs(scanVelocity);
            accel = checkMinMax(velocity - scanVelocity / (now - scanLastUpdate), isDecelerate ? -DECELERATION : -ACCELERATION, ACCELERATION);

            vMax = max(vMax, velocity);
            vMin = min(vMin, velocity);

            moveLog.add(new Move(getKDPoint(gpBase), turn, velocity, now - scanLastUpdate));

            if (moveLog.size() > gpBase.aimingMoveLogSize) {
                if (moveLog.size() > gpBase.moveLogMaxSize)
                    moveLog.remove(0);
                Move m = moveLog.get(0);
               if (kdtree == null) kdtree = new KdTree<>(m.getKdpoint().length);
                 kdtree.addPoint(m.getKdpoint(), new ArrayList<>(moveLog.subList(0, gpBase.aimingMoveLogSize)));
            }
        }
        alive = true;
        setEnergy(sre.getEnergy());
        scanLastUpdate = lastUpdate = now;
        scanVelocity = velocity;
        scanDirection = direction;
        //out.printf("%s x=%d y=%d a=%d dir=%d\n", name, (int) x, (int) y, degree(angle), degree(direction));
    }

    public double[] getKDPoint(GPBase robot) {
        return new double[]{
            //(robot.getOthers() != 0) ? 100: 0,
            trigoAngle(rDirection-getAngle(this, robot.getCurrentPoint())) * 100 / PI,
            velocity * 100 / MAX_VELOCITY,
            //energy / 10,
            //velocityVarianceMax != 0 ? velocityVariance * 50 / velocityVarianceMax : 0,
            //turnVarianceMax != 0 ? turnVariance * 50 / turnVarianceMax : 0,
            //robot.conerDistance(this) * 50 / robot.dmax,
            robot.getCurrentPoint().distance(this) * 20 / GPBase.dmax,
            //normalRelativeAngle(trigoAngle(robot.getHeadingRadians()) - angle) * 10 /PI,
            //robot.getVelocity() * 10 / MAX_VELOCITY,
            //((double) robot.getTime() - robot.lastFireTime) / GPBase.FIRE_AGAIN_MIN_TIME / 100,
        };
    }

    // Getters
    public String getName() {
        return name;
    }

    public double getVelocity() {
        return velocity;
    }

    public double getDirection() {
        return direction;
    }

    public double getEnergy() {
        return energy;
    }

    public double getAngle() {
        return angle;
    }

    public void setEnergy(double energy) {
        double delta = energy - this.energy;
        fEnergy += delta;
        this.energy = energy;
    }

    public double getvMax() {
        return vMax;
    }

    public double getvMin() {
        return vMin;
    }

    public double getRotationRate() {
        return rotationRate;
    }

    public double getAccel() {
        return accel;
    }

    public double getVelocityVariance() {
        return velocityVariance;
    }

    public double getTurnVariance() {
        if (velocity == 0) return 0;
        return turnVariance;
    }

    public double getVelocityVarianceMax() {
        return velocityVarianceMax;
    }

    public double getTurnVarianceMax() {
        return turnVarianceMax;
    }

    public KdTree<List<Move>> getKdtree() {
        return kdtree;
    }

    public List<Move> getMoveLog() {
        return moveLog;
    }

    public GPBase getGpBase() {
        return gpBase;
    }


}
