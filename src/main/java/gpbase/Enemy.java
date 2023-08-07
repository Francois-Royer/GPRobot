package gpbase;

import gpbase.gun.AimingData;
import gpbase.kdtree.KdTree;
import robocode.ScannedRobotEvent;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static gpbase.GPBase.*;
import static gpbase.GPUtils.*;
import static java.lang.Math.*;
import static robocode.Rules.*;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;



public class Enemy extends Point.Double implements Tank {
    private static final int KDTREE_MAX_SIZE = 1000;
    private final double VARIANCE_SAMPLING = 10;
    private final String name;
    private double velocity;
    private double direction; // enemy direction
    private double angle; // angle from current pos to this enemy
    private double accel = 0;
    private double turn;
    private double rotationRate = 0;
    private double fEnergy;
    private long lastUpdate; // Updated by movement prediction
    private double vMax = 0;
    private double vMin = 0;
    private double velocityVariance = 0;
    private double turnVariance = 0;
    private double velocityVarianceMax = 0;
    private double turnVarianceMax = 0;
    private long scanCount = 0;
    private double prevDirection;
    private double prevVelocity;
    private long prevUpdate;
    private long lastStop;
    private Boolean alive = true;
    private final List<Move> moveLog = new LinkedList<Move>();
    private GPBase gpBase;
    private int hitMe = 0;
    private double energy;
    private KdTree<List<Move>> patternKdTree = null;
    private KdTree<List<Move>> surferKdTree = null;
    private KdTree<java.lang.Double> fireKdTree = null;
    double gunHeat=3;

    public Enemy(ScannedRobotEvent sre, String name, GPBase gpBase, ArrayList<Wave> waves) {
        this.name = name;
        gpBase.getGunHeat();
        update(sre, gpBase, waves);
        this.patternKdTree = new KdTree.WeightedManhattan<>(getPatternPoint().length, KDTREE_MAX_SIZE);
        this.surferKdTree = new KdTree.WeightedManhattan<>(getSurferPoint().length, KDTREE_MAX_SIZE);
    }

    public void update(ScannedRobotEvent sre, GPBase gpBase, ArrayList<Wave> waves) {
        this.gpBase = gpBase;
        long now = gpBase.now;
        double sreNRG = sre.getEnergy();
        double distance = sre.getDistance();
        scanCount++;

        if (signum(velocity) != signum(sre.getVelocity()))
            lastStop = now;
        velocity = sre.getVelocity();
        gunHeat = max(0, gunHeat - GUN_COOLING_RATE * (now-lastUpdate));

        checkEnemyFire(gpBase, now, sreNRG, waves);

        direction = trigoAngle(sre.getHeadingRadians());
        angle = trigoAngle(gpBase.getHeadingRadians() + sre.getBearingRadians());

        x = gpBase.getX() + distance * cos(angle);
        y = gpBase.getY() + distance * sin(angle);

        if (scanCount > 1) {
            double prevTurn = turn;
            turn = normalRelativeAngle(direction - prevDirection);

            // Compute variances
            velocityVariance = (abs(velocity - prevVelocity) / (now - prevUpdate) + velocityVariance * (VARIANCE_SAMPLING - 1)) / VARIANCE_SAMPLING;
            velocityVarianceMax = max(velocityVariance, velocityVarianceMax);
            turnVariance = (abs(turn - prevTurn) / (now - prevUpdate) + turnVariance * (VARIANCE_SAMPLING - 1)) / VARIANCE_SAMPLING;
            turnVarianceMax = max(turnVariance, turnVarianceMax);

            rotationRate = turn / (now - prevUpdate);
            this.rotationRate = checkMinMax(this.rotationRate, -MAX_TURN_RATE_RADIANS, MAX_TURN_RATE_RADIANS);
            boolean isDecelerate = abs(velocity) < abs(prevVelocity);
            accel = checkMinMax(velocity - prevVelocity / (now - prevUpdate), isDecelerate ? -DECELERATION : -ACCELERATION, ACCELERATION);

            vMax = max(vMax, velocity);
            vMin = min(vMin, velocity);

            moveLog.add(new Move(getPatternPoint(), getSurferPoint(), turn, velocity, now - prevUpdate));

            if (moveLog.size() > gpBase.aimingMoveLogSize) {
                List<Move> log = new ArrayList<>(moveLog.subList(moveLog.size()-gpBase.aimingMoveLogSize, moveLog.size()));
                Move m = log.get(0);
                patternKdTree.addPoint(m.getPatternKdPoint(), log);
                surferKdTree.addPoint(m.getSurferKdPoint(), log);
                moveLog.remove(0);
            }
        }
        alive = true;
        setEnergy(sreNRG, 0);
        prevUpdate = lastUpdate = now;
        prevVelocity = velocity;
        prevDirection = direction;
    }

    public void move(long iteration) {
        for (long i = 0; i < iteration; i++) {
            velocity = checkMinMax(velocity + accel, vMin, vMax);
            direction += min(abs(rotationRate), getTurnRateRadians(velocity)) * signum(rotationRate);
            try {
                double h = ensureXInBatleField(x + velocity * cos(direction));
                double v = ensureYInBatleField(y + velocity * sin(direction));
                x = h;
                y = v;
            } catch (Exception ex) {
                // HitWall
            }
        }

        angle = normalAbsoluteAngle(GPUtils.getAngle(gpBase.getCurrentPoint(), this));
        lastUpdate += iteration;
        gunHeat -= GUN_COOLING_RATE*iteration;
        if (gunHeat<0) gunHeat=0;
    }

    public void die() {
        gunHeat=3;
        energy = fEnergy = rotationRate = velocity = prevVelocity = 0;
        lastUpdate = prevUpdate = 0;
        alive = false;
    }

    private void checkEnemyFire(GPBase gpBase, long now, double sreNRG, ArrayList<Wave> waves) {
        double drop = min(energy - sreNRG, MAX_BULLET_POWER);
        if (drop < MIN_BULLET_POWER || gunHeat>0)
            return;

        gunHeat = 1 + (drop / 5);
        waves.add(new Wave(this, drop, prevUpdate, this, gpBase, getFireKdPoint(drop)));

        if (gpBase.aliveCount == 1 && abs(gpBase.getVelocity()) < 1)
            // defense fire can be done
            gpBase.defenseFire = true;
    }

    public double[] getPatternPoint() {
        return new double[]{
                getForwardWallDistance()/max(FIELD_WIDTH,FIELD_HEIGHT),
                velocity / MAX_VELOCITY,
                (velocity >= 0) ? 1 : 0,
                rotationRate / MAX_TURN_RATE_RADIANS,
                (rotationRate>= 0) ? 1 : 0,
                energy == 0 ?  1 : 0
        };
    }

    public double[] getSurferPoint() {
        return new double[]{
                getForwardWallDistance()/max(FIELD_WIDTH,FIELD_HEIGHT),
                velocity / MAX_VELOCITY,
                (velocity >= 0) ? 1 : 0,
                rotationRate / MAX_TURN_RATE_RADIANS,
                (rotationRate>= 0) ? 1 : 0,
                energy == 0 ?  1 : 0,
                gpBase.getCurrentPoint().distance(this)/DISTANCE_MAX,
                normalRelativeAngle(direction-angle)/PI
        };
    }

    public double[] getFireKdPoint(double firePower) {
        return new double[]{
                angle * 50 / PI,
                distance(gpBase.getCurrentPoint())/DISTANCE_MAX*100,
                gpBase.getVelocity() / MAX_VELOCITY*100,
                gpBase.getHeadingRadians() * 50 / PI,
                (gpBase.getVelocity() >= 0) ? 100 : 0,
                firePower/MAX_BULLET_POWER*100
        };
    }

    public void addKDFire(double []fireKdPoint, double angle) {
        if (fireKdTree == null) fireKdTree = new KdTree.SqrEuclid<java.lang.Double>(fireKdPoint.length, KDTREE_MAX_SIZE);
        fireKdTree.addPoint(fireKdPoint, angle);
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

    public void setEnergy(double energy, ArrayList<AimingData> aimDatas) {
        this.energy = energy;
        this.fEnergy = energy;
        aimDatas.stream().filter(a -> a.getTarget() == this)
                .forEach(a -> this.fEnergy -= getBulletDamage(a.getFirePower()));
    }

    @Override
    public double getAngle() {
        return angle;
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

    public long getLastUpdate() {
        return lastUpdate;
    }

    public long getPrevUpdate() {
        return prevUpdate;

    }
    public Boolean isAlive() {
        return alive;
    }


    public double getFEnergy() { return fEnergy; }
    public void addFEnergy(double v) { fEnergy += v; }

    public void removeFEnergy(double v) { fEnergy -= v; }
    public int getHitMe() { return hitMe; }

    public void hitMe() { hitMe++; }
    public long getLastUpdateDelta() {
        return  lastUpdate- prevUpdate;
    }

    public KdTree<List<Move>> getPatternKdTree() {
        return patternKdTree;
    }

    public KdTree<List<Move>> getSurferKdTree() {
        return surferKdTree;
    }

    public KdTree<java.lang.Double> getFireKdTree() {
        return fireKdTree;
    }
    public GPBase getGpBase() {
        return gpBase;
    }

    public List<Move> getMoveLog() {
        return moveLog;
    }

    public double getWallDistance() {
        return sqrt(pow(getWallDistanceX(),2)+pow(getWallDistanceY(),2));
    }

    public double getForwardWallDistance() {
        return distance(wallIntersection(this, getDynamicDirection()));
    }

    public double getWallDistanceX() {
        return min(x, FIELD_WIDTH - x);
    }
    public double getWallDistanceY() {
        return min(y, FIELD_HEIGHT - y);
    }
    public double getClosestWallDistance() {
        return min(getWallDistanceX(), getWallDistanceY());
    }
    public double getDanger(int x, int y, int maxHitMe) {
        double d = sqrt(pow(x - getX()/DANGER_SCALE, 2) + pow(y - getY()/DANGER_SCALE, 2));
        if (d > MAX_DANGER_RADIUS) {
            double danger = Math.pow((DANGER_DISTANCE_MAX - d + MAX_DANGER_RADIUS) / DANGER_DISTANCE_MAX, 2);
            return danger * (hitMe + 1) / (maxHitMe + 1);
        }
        return  1;
    }

    public double getDynamicDirection() {
        return normalRelativeAngle(direction + ((velocity>=0)?0:PI));
    }
}

