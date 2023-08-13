package tankbase;

import tankbase.gun.AbstractKdTreeGunner;
import tankbase.gun.AimingData;
import tankbase.kdtree.KdTree;
import robocode.ScannedRobotEvent;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static tankbase.TankBase.*;
import static tankbase.TankUtils.*;
import static java.lang.Math.*;
import static robocode.Rules.*;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;



public class Enemy extends Point.Double implements ITank {
    private static final int KDTREE_MAX_SIZE = 1000;
    private final double VARIANCE_SAMPLING = 10;
    private final String name;
    private double velocity;
    private double headingRadians; // enemy direction
    private double accel = 0;
    private double turn;
    private double turnRate = 0;
    private double fEnergy;
    private long lastUpdate; // Updated by movement prediction
    private double vMax = 0;
    private double vMin = 0;
    private double prevHeadingRadians;
    private double prevVelocity;
    private long prevUpdate;
    private Boolean alive = false;
    private boolean isDecelerate = false;
    private final List<Move> moveLog = new LinkedList<Move>();
    private TankBase tankBase;
    private int hitMe = 0;
    private double energy;
    private KdTree.WeightedManhattan<List<Move>> patternKdTree = null;
    private KdTree.WeightedManhattan<List<Move>> surferKdTree = null;
    double gunHeat=3;
    double angle;

    public Enemy(ScannedRobotEvent sre, String name, TankBase tankBase, ArrayList<Wave> waves) {
        this.name = name;
        update(sre, tankBase, waves);
        patternKdTree = new KdTree.WeightedManhattan<>(AbstractKdTreeGunner.getPatternPoint(this).length, KDTREE_MAX_SIZE);
        patternKdTree.setWeights(AbstractKdTreeGunner.patternWeights);
        surferKdTree = new KdTree.WeightedManhattan<>(AbstractKdTreeGunner.getSurferPoint(this, tankBase).length, KDTREE_MAX_SIZE);
        surferKdTree.setWeights(AbstractKdTreeGunner.surferWeights);
    }

    public void update(ScannedRobotEvent sre, TankBase tankBase, ArrayList<Wave> waves) {
        this.tankBase = tankBase;
        long now = tankBase.now;
        double sreNRG = sre.getEnergy();
        double distance = sre.getDistance();

        velocity = sre.getVelocity();
        gunHeat = max(0, gunHeat - GUN_COOLING_RATE * (now-lastUpdate));
        headingRadians = trigoAngle(sre.getHeadingRadians());
        angle = normalAbsoluteAngle(tankBase.getHeadingRadians() - sre.getBearingRadians());

        if (alive) {
            checkEnemyFire(tankBase, now, sreNRG, waves);
            double prevTurn = turn;
            turn = normalRelativeAngle(headingRadians - prevHeadingRadians);

            turnRate = turn / (now - prevUpdate);
            this.turnRate = checkMinMax(this.turnRate, -MAX_TURN_RATE_RADIANS, MAX_TURN_RATE_RADIANS);
            isDecelerate = abs(velocity) < abs(prevVelocity);
            accel = velocity - prevVelocity / (now - prevUpdate);
            vMax = max(vMax, velocity);
            vMin = min(vMin, velocity);

            moveLog.add(new Move(AbstractKdTreeGunner.getPatternPoint(this),
                    AbstractKdTreeGunner.getSurferPoint(this, tankBase),
                    turn, velocity, now - prevUpdate));

            if (moveLog.size() > tankBase.aimingMoveLogSize) {
                List<Move> log = new ArrayList<>(moveLog.subList(moveLog.size()- tankBase.aimingMoveLogSize, moveLog.size()));
                Move m = log.get(0);
                patternKdTree.addPoint(m.getPatternKdPoint(), log);
                surferKdTree.addPoint(m.getSurferKdPoint(), log);
                moveLog.remove(0);
            }
        } else
            fEnergy = energy;

        x = tankBase.getPosition().getX() + distance * cos(angle);
        y = tankBase.getPosition().getY() + distance * sin(angle);

        alive = true;
        setEnergy(sreNRG, true);
        prevUpdate = lastUpdate = now;
        prevVelocity = velocity;
        prevHeadingRadians = headingRadians;
    }

    public void move(long iteration) {
        for (long i = 0; i < iteration; i++) {
            velocity = checkMinMax(velocity + accel, vMin, vMax);
            headingRadians += min(abs(turnRate), getTurnRateRadians(velocity)) * signum(turnRate);
            try {
                double h = ensureXInBatleField(x + velocity * cos(headingRadians));
                double v = ensureYInBatleField(y + velocity * sin(headingRadians));
                x = h;
                y = v;
            } catch (Exception ex) {
                // HitWall
            }
        }

        lastUpdate += iteration;
        gunHeat -= GUN_COOLING_RATE*iteration;
        if (gunHeat<0) gunHeat=0;
    }

    public void die() {
        gunHeat=3;
        energy = fEnergy = turnRate = velocity = prevVelocity = 0;
        lastUpdate = prevUpdate = 0;
        alive = false;
    }

    private void checkEnemyFire(TankBase tankBase, long now, double sreNRG, ArrayList<Wave> waves) {
        double drop = min(energy - sreNRG, MAX_BULLET_POWER);
        if (drop < MIN_BULLET_POWER || gunHeat>0)
            return;

        waves.add(new Wave(this, drop, prevUpdate, this, tankBase));

        if (tankBase.aliveCount == 1)
            // defense fire can be done
            tankBase.defenseFire = true;
    }

    // Getters
    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getVelocity() {
        return velocity;
    }

    @Override
    public double getHeadingRadians() {
        return headingRadians;
    }

    @Override
    public double getEnergy() {
        return energy;
    }

    @Override
    public double getVMax() {
        return vMax;
    }

    @Override
    public double getVMin() {
        return vMin;
    }

    public void setEnergy(double energy) {
        setEnergy(energy, false);
    }

    public void setEnergy(double energy, boolean updateFenergy) {
        if (updateFenergy) {
            double delta = this.energy-energy;
            fEnergy -= delta;
        }
        this.energy = energy;
    }

    public double getAngle() {
        return angle;
    }

    @Override
    public double getTurnRate() {
        return turnRate;
    }

    @Override
    public double getAccel() {
        return accel;
    }

    @Override
    public Point.Double getPosition() {
        return new Point.Double(getX(), getY());
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public long getPrevUpdate() {
        return prevUpdate;

    }

    @Override
    public boolean isAlive() {
        return alive;
    }


    public double getFEnergy() { return fEnergy; }
    public void addFEnergy(double v) {
        fEnergy += v;
    }

    @Override
    public int getAliveCount() {
        return tankBase.getAliveCount();
    }

    public int getHitMe() { return hitMe; }

    public void hitMe() { hitMe++; }
    public long getLastUpdateDelta() {
        return  lastUpdate-prevUpdate;
    }

    public KdTree<List<Move>> getPatternKdTree() {
        return patternKdTree;
    }

    public KdTree<List<Move>> getSurferKdTree() {
        return surferKdTree;
    }

    public TankBase getGpBase() {
        return tankBase;
    }

    @Override
    public List<Move> getMoveLog() {
        return moveLog;
    }

    @Override
    public double hit() {
        return 0;
    }

    public double getWallDistance() {
        return sqrt(pow(getWallDistanceX(),2)+pow(getWallDistanceY(),2));
    }

    public double getForwardWallDistance() {
        return distance(wallIntersection(this, getMovingDirection()));
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

    public double getMovingDirection() {
        return normalRelativeAngle(headingRadians + ((velocity>=0)?0:PI));
    }

    @Override
    public boolean isDecelerate() {
        return isDecelerate;
    }

    public List<AimingData> getAimingLog(String target){
        return new ArrayList<>();
    }
}

