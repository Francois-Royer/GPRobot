package gpbase;

import gpbase.gun.AimingData;
import gpbase.kdtree.KdTree;
import robocode.ScannedRobotEvent;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
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
    private double scanDirection;
    private double scanVelocity;
    private long scanLastUpdate;
    private long lastFire;
    private Boolean alive = true;
    private final ArrayList<Move> moveLog = new ArrayList<>();
    private GPBase gpBase;
    private int hitMe = 0;
    private double energy;
    private KdTree<List<Move>> moveKdTree = null;
    private KdTree<java.lang.Double> fireKdTree = null;
    double gunHeat=3;

    public Enemy(ScannedRobotEvent sre, String name, GPBase gpBase, ArrayList<Wave> waves) {
        this.name = name;
        gpBase.getGunHeat();
        update(sre, gpBase, waves);
    }

    public void update(ScannedRobotEvent sre, GPBase gpBase, ArrayList<Wave> waves) {
        this.gpBase = gpBase;
        long now = gpBase.now;
        double sreNRG = sre.getEnergy();
        double distance = sre.getDistance();
        scanCount++;
        velocity = sre.getVelocity();
        gunHeat -= GUN_COOLING_RATE * (now-lastUpdate);
        if (gunHeat<0) gunHeat=0;

        checkEnemyFire(gpBase, now, sreNRG, waves);

        direction = trigoAngle(sre.getHeadingRadians());
        angle = trigoAngle(gpBase.getHeadingRadians() + sre.getBearingRadians());

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

            moveLog.add(new Move(getMoveKdPoint(), turn, velocity, now - scanLastUpdate));

            if (moveLog.size() >= gpBase.aimingMoveLogSize) {
                Move m = moveLog.get(gpBase.aimingMoveLogSize - 1);
                if (moveKdTree == null) moveKdTree = new KdTree.SqrEuclid<>(m.getKdpoint().length, KDTREE_MAX_SIZE);
                try {
                    List<Move> lm = new ArrayList<>(moveLog.subList(0, gpBase.aimingMoveLogSize));
                    Collections.reverse(lm);
                    moveKdTree.addPoint(m.getKdpoint(), lm);
                } catch (Exception e) {
                }
                if (moveLog.size() > gpBase.moveLogMaxSize)
                    moveLog.remove(0);
            }
        }
        alive = true;
        setEnergy(sreNRG, 0);
        scanLastUpdate = lastUpdate = now;
        scanVelocity = velocity;
        scanDirection = direction;
        //out.printf("%s x=%d y=%d a=%d dir=%d\n", name, (int) x, (int) y, degree(angle), degree(direction));
    }

    public void move(long iteration, long now) {
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
        lastUpdate = now;
        gunHeat -= GUN_COOLING_RATE*iteration;
        if (gunHeat<0) gunHeat=0;
    }

    public void die() {
        gunHeat=3;
        energy = fEnergy = rotationRate = velocity = scanVelocity = 0;
        scanLastUpdate = lastFire = 0;
        alive = false;
    }

    private void checkEnemyFire(GPBase gpBase, long now, double sreNRG, ArrayList<Wave> waves) {
        //if (gpBase.aliveCount<3)
            if (energy > sreNRG && this.lastFire + FIRE_AGAIN_MIN_TIME < now) {
                double drop = energy - sreNRG;
                if (drop >= MIN_BULLET_POWER && drop <=MAX_BULLET_POWER) {
                    gunHeat=1 + (drop / 5);
                    waves.add(new Wave(this, drop, scanLastUpdate, this, gpBase, getFireKdPoint(drop)));
                    this.lastFire = scanLastUpdate;

                    if (gpBase.aliveCount == 1)
                        // defense fire can be done
                        gpBase.defenseFire = true;
                }
            }
    }

    public double[] getMoveKdPoint() {
        return new double[]{
                gpBase.aliveCount > 1 ? 1: 0,
                normalAbsoluteAngle(direction) / PI,
                velocity / MAX_VELOCITY,
                (velocity >= 0) ? 1 : 0,
                rotationRate / MAX_TURN_RATE_RADIANS,
                (rotationRate >= 0) ? 1 : 0,
                energy,
                gpBase.getCurrentPoint().distance(this) / GPBase.DISTANCE_MAX
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

    public long getScanLastUpdate() {
        return scanLastUpdate;

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
        return  lastUpdate-scanLastUpdate;
    }

    public KdTree<List<Move>> getMoveKdTree() {
        return moveKdTree;
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
        return min(min(x, FIELD_WIDTH - x), min(y, FIELD_HEIGHT - y));
    }

    public double getDanger(int x, int y, int maxHitMe) {
        double d = sqrt(pow(x - getX()/DANGER_SCALE, 2) + pow(y - getY()/DANGER_SCALE, 2));
        if (d > MAX_DANGER_RADIUS) {
            double danger = Math.pow((DANGER_DISTANCE_MAX - d + MAX_DANGER_RADIUS) / DANGER_DISTANCE_MAX, 8);
            return danger * (hitMe + 1) / (maxHitMe + 1);
        }
        return  1;
    }
}

