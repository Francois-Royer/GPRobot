package gpbase;

import java.awt.*;
import java.util.ArrayList;

import robocode.*;
import gpbase.dataStructures.trees.KD.KdTree;
import gpbase.dataStructures.trees.KD.NearestNeighborIterator;
import gpbase.dataStructures.trees.KD.SquareEuclideanDistanceFunction;

import static java.lang.Math.*;
import static gpbase.GPUtils.*;
import static robocode.util.Utils.*;
import static robocode.Rules.*;

public class Enemy extends Point.Double {
    double VARIANCE_SAMPLING = 10;
    String name;
    double energy;
    double velocity;

    double direction; // enemy direction
    double angle; // angle from current pos to this enemy
    double rDirection; // direction - angle

    double accel = 0;
    double turn;
    double rotationRate = 0;
    long lastUpdate; // Updated by movement prediction

    double vMax = 0;
    double vMin = 0;
    double vAvg = 0;
    double velocityVariance=0;
    double turnVariance=0;
    double velocityVarianceMax=0;
    double turnVarianceMax=0;

    long fireCount = 0;
    long bulletHitCount = 0;
    long lastFire = 0;

    long scanCount = 0;
    double scanDirection;
    double scanVelocity;
    long scanLastUpdate;

    Boolean alive = true;

    KdTree<ArrayList<Move>> kdtree = null;
    ArrayList<Move> moveLog = new ArrayList<>();

    public Enemy(ScannedRobotEvent sre, GPBase robot) {
        name = sre.getName();
        update(sre, robot);
    }

    public void update(ScannedRobotEvent sre, GPBase robot) {
        long now= robot.getTime();
        scanCount++;

        if (scanCount>1) {
            if (energy > sre.getEnergy() && this.lastFire + robot.FIRE_AGAIN_MIN_TIME < now) {
                double bspeed = getBulletSpeed(
                        checkMinMax(energy - sre.getEnergy(), MIN_BULLET_POWER, MAX_BULLET_POWER));
                robot.waves.add(new Wave(name, bspeed, scanLastUpdate, this, robot));
                this.lastFire = scanLastUpdate;
            }
        }

        velocity = sre.getVelocity();
        direction = trigoAngle(sre.getHeadingRadians());
        angle = trigoAngle(robot.getHeadingRadians() + sre.getBearingRadians());
        rDirection = normalRelativeAngle(direction - angle);
        x = robot.getX() + sre.getDistance() * cos(angle);
        y = robot.getY() + sre.getDistance() * sin(angle);

        if (alive & scanCount>1) {
            double prevTurn = turn;
            turn = normalRelativeAngle(direction - scanDirection);

            // Compute variances
            velocityVariance = (abs(velocity-scanVelocity)/(now-scanLastUpdate) + velocityVariance*(VARIANCE_SAMPLING-1))/VARIANCE_SAMPLING;
            velocityVarianceMax = max(velocityVariance, velocityVarianceMax);
            turnVariance = (abs(turn-prevTurn)/(now-scanLastUpdate) + turnVariance*(VARIANCE_SAMPLING-1)) / VARIANCE_SAMPLING;
            turnVarianceMax = max(turnVariance, turnVarianceMax);

            rotationRate = turn / (now - scanLastUpdate);
            this.rotationRate = checkMinMax(this.rotationRate, -MAX_TURN_RATE_RADIANS, MAX_TURN_RATE_RADIANS);
            boolean isDecelerate = abs(velocity)<abs(scanVelocity);
            accel = checkMinMax(velocity - scanVelocity / (now - scanLastUpdate),isDecelerate?  -DECELERATION: -ACCELERATION, ACCELERATION);

            vMax = max(vMax, velocity);
            vMin = min(vMin, velocity);

            moveLog.add(new Move(getKDPoint(robot), turn, velocity, now - scanLastUpdate));

            if (moveLog.size()> robot.phsz) {
                moveLog.remove(0);
                Move m  = moveLog.get(0);
                if (kdtree == null) kdtree = new KdTree<>(m.getKdpoint().length);
                kdtree.addPoint(m.getKdpoint(), new ArrayList<Move>(moveLog));
            }
        }

        alive = true;
        energy = sre.getEnergy();
        scanLastUpdate = lastUpdate = now;
        scanVelocity = velocity;
        scanDirection = direction;
        //out.printf("%s x=%d y=%d a=%d dir=%d\n", name, (int) x, (int) y, degree(angle), degree(direction));
    }

    double getHitRate() {
        return (this.fireCount == 0) ? 0 : (double) this.bulletHitCount / this.fireCount;
    }

    public double[] getKDPoint(GPBase robot) {
        return new double[] {
                rDirection * 100 / PI,
                velocity * 100 / MAX_VELOCITY,
                velocityVariance * 100 / velocityVarianceMax,
                turnVariance* 100/turnVarianceMax,
                //m.lastFire * 100 / GPBase.FIRE_AGAIN_MIN_TIME,
                //normalRelativeAngle(trigoAngle(robot.getHeadingRadians()) - angle) * 100 /PI,
                //robot.getVelocity() * 100 / MAX_VELOCITY,
                robot.getCurrentPoint().distance(this) * 100 / GPBase.dmax,
                //robot.wallDistance(this) * 100 / robot.BATTLE_FIELD_CENTER.getY()
        };
    }

    ArrayList<Move> getPredictedMove(GPBase robot) {
        if (kdtree == null) return null;
        NearestNeighborIterator<ArrayList<Move>> it  = kdtree.getNearestNeighborIterator(getKDPoint(robot), 1, new SquareEuclideanDistanceFunction());
        if (it.hasNext()) {
            ArrayList<Move> moves = it.next();
            double distance = it.distance();

            if (distance < 50) {
                //System.out.println("distance=" + distance);
                return moves;
            }
        }
        return null;
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

    public double getVelocityVariance() { return velocityVariance; }

    public double getTurnVariance() { return turnVariance; }

    public double getVelocityVarianceMax() { return velocityVarianceMax; }

    public double getTurnVarianceMax() { return turnVarianceMax; }

    public KdTree<ArrayList<Move>> getKdtree() {
        return kdtree;
    }

    public ArrayList<Move> getMoveLog() {
        return moveLog;
    }


}
