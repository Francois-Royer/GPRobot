package gpbase;

import java.awt.*;
import java.util.*;
import java.util.List;

import gpbase.kdtree.KdTree;
import robocode.*;

import static java.lang.Math.*;
import static gpbase.GPUtils.*;
import static robocode.util.Utils.*;
import static robocode.Rules.*;


public class Enemy extends Point.Double implements Tank {
    private static int KDTREE_MAX_SIZE = 1000;
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

    long lifeTime=0;

    Boolean alive = true;

    private KdTree<List<Move>> kdTree = null;
    ArrayList<Move> moveLog = new ArrayList<>();

    GPBase gpBase;

    long hitMe=0;

    public Enemy(ScannedRobotEvent sre, String name, GPBase gpBase) {
        this.name = name;
        update(sre, gpBase);
    }

    public void update(ScannedRobotEvent sre, GPBase gpBase) {
        this.gpBase = gpBase;
        long now = gpBase.now;
        double  sreNRG = sre.getEnergy();
        double distance = sre.getDistance();
        scanCount++;
        velocity = sre.getVelocity();

            if (energy > sreNRG && this.lastFire + gpBase.FIRE_AGAIN_MIN_TIME < now) {
                double drop = energy - sreNRG;
                if (drop >= MIN_BULLET_POWER && drop <= MAX_BULLET_POWER &&
                        (getWallDistance() > GPBase.TANK_SIZE/2 || velocity>0)) {
                    double bspeed = getBulletSpeed(drop);
                    gpBase.waves.add(new Wave(name, bspeed, scanLastUpdate, this, gpBase));
                    this.lastFire = scanLastUpdate;
                }
            }

        direction = trigoAngle(sre.getHeadingRadians());
        angle = trigoAngle(gpBase.getHeadingRadians() + sre.getBearingRadians());
        rDirection = normalRelativeAngle(direction - angle);
        x = gpBase.getX() + distance * cos(angle);
        y = gpBase.getY() + distance * sin(angle);

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

            if (moveLog.size() >= gpBase.aimingMoveLogSize) {
                Move m = moveLog.get(gpBase.aimingMoveLogSize-1);
                if (kdTree == null) kdTree = new KdTree.SqrEuclid<List<Move>>(m.getKdpoint().length, KDTREE_MAX_SIZE);
                try {
                    List<Move> lm = new ArrayList<>(moveLog.subList(0, gpBase.aimingMoveLogSize));
                    Collections.reverse(lm);
                    kdTree.addPoint(m.getKdpoint(), lm);
                } catch (Exception e) {
                }
                if (moveLog.size() > gpBase.moveLogMaxSize)
                    moveLog.remove(0);
            }
        }
        alive = true;
        setEnergy(sreNRG);
        scanLastUpdate = lastUpdate = now;
        scanVelocity = velocity;
        scanDirection = direction;
        //out.printf("%s x=%d y=%d a=%d dir=%d\n", name, (int) x, (int) y, degree(angle), degree(direction));
    }

    public double[] getKDPoint(GPBase robot) {
        return new double[] {
                getX() * 200 / robot.FIELD_WIDTH,
                getY() * 200 / robot.FIELD_HEIGHT,
                (normalAbsoluteAngle(direction))*200 / 2 / PI,
                velocity * 200 / MAX_VELOCITY,
                (velocity >= 0) ? 100 : 0,
                //accel * 100 / DECELERATION,
                //(accel>=0) ? 100 : 0,
                rotationRate * 100 / MAX_TURN_RATE_RADIANS,
                (rotationRate>=0) ? 100 : 0,
                // robot.enemyCount* 100 /robot.aliveCount,
                //robot.getCurrentPoint().distance(this) * 100 / GPBase.dmax,
                //normalAbsoluteAngle(direction-GPUtils.getAngle(this, robot.getCurrentPoint())) * 100 / 2 / PI,
                energy,
                //robot.getEnergy(),
                //(1/(robot.conerDistance(this)+ java.lang.Double.MIN_VALUE)) * 100 / robot.dmax,
                //((double) robot.getTime() - robot.lastFireTime) / GPBase.FIRE_AGAIN_MIN_TIME / 100
        };
    }

    // Getters
    public String getName() {
        return name;
    }

    @Override
    public double getVelocity() {
        return velocity;
    }

    @Override
    public double getDirection() {
        return direction;
    }

    @Override
    public double getEnergy() {
        return energy;
    }

    @Override
    public double getAngle() {
        return angle;
    }

    public void setEnergy(double energy) {
        double delta = energy - this.energy;
        fEnergy += delta;
        this.energy = energy;
    }

    public void setEnergy(double energy, double bulletDommage) {
        double delta = energy - this.energy;
        fEnergy += delta + bulletDommage;
        this.energy = energy;
    }

    @Override
    public double getvMax() {
        return vMax;
    }

    @Override
    public double getvMin() {
        return vMin;
    }

    @Override
    public double getRotationRate() {
        return rotationRate;
    }

    @Override
    public double getAccel() {
        return accel;
    }

    @Override
    public double getVelocityVariance() {
        return velocityVariance;
    }

    @Override
    public double getTurnVariance() {
        if (velocity == 0) return 0;
        return turnVariance;
    }

    @Override
    public double getVelocityVarianceMax() {
        return velocityVarianceMax;
    }

    @Override
    public double getTurnVarianceMax() {
        return turnVarianceMax;
    }

    public KdTree<List<Move>> getKdTree() {
        return kdTree;
    }

    public List<Move> getMoveLog() {
        return moveLog;
    }

    public GPBase getGpBase() {
        return gpBase;
    }

    public double getWallDistance() {
        return min(min(x, gpBase.FIELD_WIDTH-x), min(y, gpBase.FIELD_HEIGHT-y));
    }
}

