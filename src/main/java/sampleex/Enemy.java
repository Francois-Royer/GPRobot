package sampleex;

import java.awt.*;
import java.util.ArrayList;

import robocode.*;
import sampleex.dataStructures.trees.KD.KdTree;
import sampleex.dataStructures.trees.KD.NearestNeighborIterator;
import sampleex.dataStructures.trees.KD.SquareEuclideanDistanceFunction;

import static java.lang.Math.*;
import static sampleex.GPUtils.*;
import static robocode.util.Utils.*;
import static robocode.Rules.*;

class Enemy extends Point.Double {
    String name;
    double energy;
    double velocity;

    double direction; // enemy direction
    double angle; // angle from current pos to this enemy
    double rDirection; // direction - angle

    double vMax = 0;
    double vMin = 0;
    double vAvg = 0;
    double rotationRate = 0;

    double accel = 0;
    double turn;
    long lastUpdate; // Update by movement prediction

    long fireCount = 0;
    long bulletHitCount = 0;
    long lastFire = 0;

    long scanCount = 0;
    double scanDirection;
    double scanVelocity = 0;
    long scanLastUpdate = 0 ;

    Boolean alive = true;

    KdTree<ArrayList<EnemyState>> kdtree = null;
    ArrayList<EnemyState> moveLog = new ArrayList<>();

    public Enemy(ScannedRobotEvent sre, GPBase robot) {
        name = sre.getName();
        update(sre, robot);
    }

    public void update(ScannedRobotEvent sre, GPBase robot) {
        long now= robot.getTime();
        scanCount++;
        velocity = sre.getVelocity();
        direction = trigoAngle(sre.getHeadingRadians());
        angle = trigoAngle(robot.getHeadingRadians() + sre.getBearingRadians());
        rDirection = normalRelativeAngle(direction - angle);
        x = robot.getX() + sre.getDistance() * cos(angle);
        y = robot.getY() + sre.getDistance() * sin(angle);

        if (alive & scanLastUpdate>0) {
            rotationRate = normalRelativeAngle(direction - scanDirection) / (now - scanLastUpdate);
            this.rotationRate = checkMinMax(this.rotationRate, -MAX_TURN_RATE_RADIANS, MAX_TURN_RATE_RADIANS);
            accel = checkMinMax(velocity - scanVelocity / (now - scanLastUpdate), -DECELERATION, ACCELERATION);

            vAvg = (abs(velocity) + vAvg) / 10;
            vMax = max(vMax, velocity);
            vMin = min(vMin, velocity);

            if (energy > sre.getEnergy() && this.lastFire + robot.FIRE_AGAIN_MIN_TIME < now) {
                double bspeed = getBulletSpeed(
                        checkMinMax(energy - sre.getEnergy(), MIN_BULLET_POWER, MAX_BULLET_POWER));
                robot.waves.add(new Wave(name, bspeed, lastUpdate, this, robot));
                this.lastFire = lastUpdate;
            }

            moveLog.add(new EnemyState(this, robot, now-scanLastUpdate));
            if (moveLog.size()> robot.phsz) {
                moveLog.remove(0);
                double[] point = getKDPoint(moveLog.get(0));
                if (kdtree == null) kdtree = new KdTree<>(point.length);
                kdtree.addPoint(point, new ArrayList<EnemyState>(moveLog));
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

    double[] getKDPoint(EnemyState m) {
        return new double[] {
                m.direction * 100 / PI,
                m.velocity * 100 / MAX_VELOCITY,
                //m.lastFire * 100 / GPBase.FIRE_AGAIN_MIN_TIME,
                //m.rDirection * 100 /PI,
                m.rVelocity * 100 / MAX_VELOCITY,
                m.dist * 100 / GPBase.dmax
        };
    }

    double kddist;
    ArrayList<EnemyState> getPredictedMove(GPBase robot) {
        if (kdtree == null) return null;
        EnemyState m = new EnemyState(this, robot, 0);
        NearestNeighborIterator<ArrayList<EnemyState>> it  = kdtree.getNearestNeighborIterator(getKDPoint(m), 1, new SquareEuclideanDistanceFunction());
        if (it.hasNext()) {
            ArrayList<EnemyState> moves = it.next();
            double distance = it.distance();

            if (distance < 50) {
                System.out.println("distance=" + distance);
                return moves;
            }
        }
        return null;
    }
}
