package tankbase;

import robocode.Rules;
import robocode.ScannedRobotEvent;
import tankbase.gun.AbstractKdTreeGunner;
import tankbase.gun.AimingData;
import tankbase.gun.Shell;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.*;
import static robocode.Rules.*;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.TankBase.*;
import static tankbase.TankUtils.*;


public class Enemy extends Point.Double implements ITank {
    private static final int KDTREE_MAX_SIZE = 1000;
    public static final int MAX_GUN_HEAT = 3;
    private final String name;
    private double velocity;
    private double headingRadians; // enemy direction
    private double accel = 0;
    private double turn;
    private double turnRate = 0;
    private double fEnergy;
    private long lastUpdate; // Updated every turn
    private double vMax = 0;
    private double vMin = 0;
    private double prevHeadingRadians;
    private double prevVelocity;
    private double prevTurRate;
    private long lastScan;
    private long lastStop;
    private long lastChangeDirection;
    private Boolean alive = false;
    private boolean isDecelerate = false;
    private final List<Move> moveLog = new LinkedList<Move>();
    private TankBase tankBase;
    private int hitMe = 0;
    private double damageMe = 0;
    private double energy;
    private KdTree.WeightedManhattan<List<Move>> patternKdTree = null;
    private KdTree.WeightedManhattan<List<Move>> surferKdTree = null;

    private List<AimingData> turnAimDatas = new ArrayList<>();
    double gunHeat = MAX_GUN_HEAT;
    double angle;

    int fireHead = 1;
    int fireCircular = 0;

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
        long now = sre.getTime();
        double sreNRG = sre.getEnergy();
        double distance = sre.getDistance();

        velocity = sre.getVelocity();
        gunHeat = max(0, gunHeat - GUN_COOLING_RATE * (now - lastUpdate));
        headingRadians = trigoAngle(sre.getHeadingRadians());
        angle = normalAbsoluteAngle(tankBase.getHeadingRadians() - sre.getBearingRadians());

        if (alive) {
            checkEnemyFire(tankBase, now, sreNRG, waves);
            double prevTurn = turn;
            turn = normalRelativeAngle(headingRadians - prevHeadingRadians);

            turnRate = turn / (now - lastScan);
            this.turnRate = checkMinMax(this.turnRate, -MAX_TURN_RATE_RADIANS, MAX_TURN_RATE_RADIANS);
            if (velocity == 0 || signum(prevVelocity) != signum(velocity))
                lastStop = now;
            if (turnRate ==0 || signum(prevTurn) != signum(turnRate))
                lastChangeDirection = now;

            isDecelerate = abs(velocity) < abs(prevVelocity);
            accel = velocity - prevVelocity / (now - lastScan);
            vMax = max(vMax, velocity);
            vMin = min(vMin, velocity);

            moveLog.add(new Move(AbstractKdTreeGunner.getPatternPoint(this),
                    AbstractKdTreeGunner.getSurferPoint(this, tankBase),
                    turn, velocity, now - lastScan));

            if (moveLog.size() > tankBase.aimingMoveLogSize) {
                List<Move> log = new ArrayList<>(moveLog.subList(moveLog.size() - tankBase.aimingMoveLogSize, moveLog.size()));
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
        lastScan = now;
        lastUpdate = now;
        prevVelocity = velocity;
        prevTurRate = turnRate;
        prevHeadingRadians = headingRadians;
    }

    private double accelerate() {
        if (velocity == 0)
            return ACCELERATION * signum(-prevVelocity);
        double vsign = signum(velocity);
        if (isDecelerate) {
            if (abs(velocity) < DECELERATION)
                return 0;
            return velocity - DECELERATION * signum(velocity);
        }
        return checkMinMax(velocity + ACCELERATION * signum(velocity), vMin, vMax);
    }

    public void move(long iteration) {
        for (long i = 0; i < iteration; i++) {
            velocity = accelerate();
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
        gunHeat -= GUN_COOLING_RATE * iteration;
        if (gunHeat < 0) gunHeat = 0;
    }

    public void die() {
        gunHeat = MAX_GUN_HEAT;
        energy = fEnergy = turnRate = velocity = prevVelocity = 0;
        lastUpdate = lastScan = 0;
        alive = false;
    }

    private void checkEnemyFire(TankBase tankBase, long now, double sreNRG, ArrayList<Wave> waves) {
        double drop = min(energy - sreNRG, MAX_BULLET_POWER);
        if (drop < MIN_BULLET_POWER)// || gunHeat>0)
            return;

        gunHeat = Rules.getGunHeat(drop);
        waves.add(new Wave(tankBase, drop, lastScan, this, fireHead, fireCircular));
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
            double delta = this.energy - energy;
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

    public long getLastScan() {
        return lastScan;

    }

    @Override
    public boolean isAlive() {
        return alive;
    }


    public double getFEnergy() {
        return fEnergy;
    }

    public void addFEnergy(double v) {
        fEnergy += v;
    }

    @Override
    public int getAliveCount() {
        return tankBase.getAliveCount();
    }

    @Override
    public long getDate() {
        return tankBase.getDate();
    }

    @Override
    public long getLastStop() {
        return lastStop;
    }

    @Override
    public long getLastChangeDirection() {
        return lastChangeDirection;
    }

    public int getHitMe() {
        return hitMe;
    }

    public void hitMe() {
        hitMe++;
    }

    public double getDamageMe() {
        return damageMe;
    }
    public void damageMe(double damage) {
        damageMe += damage;
        hitMe();
    }

    public long getLastUpdateDelta() {
        return lastUpdate - lastScan;
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
        return sqrt(pow(getWallDistanceX(), 2) + pow(getWallDistanceY(), 2));
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

    public double getDanger(int x, int y, double maxDamageMe) {
        double d = sqrt(pow(x - getX() / DANGER_SCALE, 2) + pow(y - getY() / DANGER_SCALE, 2));
        if (d > MAX_DANGER_RADIUS) {
            boolean shadowed = getEnemys()
                    .filter(Enemy::isAlive)
                    .filter(e -> e != this)
                    .map(e -> collisionCercleSeg(e.getPosition(), TANK_SIZE, new Double(x*DANGER_SCALE, y*DANGER_SCALE), this))
                    .reduce((a, b) -> a||b)
                    .orElse(false);

            if (shadowed)
                return 0;

            double danger = Math.pow((DANGER_DISTANCE_MAX - d + MAX_DANGER_RADIUS) / DANGER_DISTANCE_MAX, 8);
            return danger * (getDamageMe() + .001) / (maxDamageMe + .001);
        }
        return 1;
    }

    public double getMovingDirection() {
        return normalRelativeAngle(headingRadians + ((velocity >= 0) ? 0 : PI));
    }

    @Override
    public double getGunHeat() {
        return gunHeat;
    }

    @Override
    public boolean isDecelerate() {
        return isDecelerate;
    }

    public List<Shell> getFireLog(String target) {
        return new ArrayList<>();
    }

    int FIRE_STAT_COUNT_MAX = 5;

    public void fireHead() {
        //tankBase.out.printf("%s hit head, %d %d\n", name, fireHead, fireCircular);
        fireHead++;
        if (fireHead + fireCircular > FIRE_STAT_COUNT_MAX) {
            if (fireHead > FIRE_STAT_COUNT_MAX) fireHead--;
            else fireCircular--;
        }
    }

    public void fireCircular() {
        //tankBase.out.printf("%s hit circular, %d %d\n", name, fireHead, fireCircular);
        fireCircular++;
        if (fireHead + fireCircular > FIRE_STAT_COUNT_MAX) {
            if (fireCircular > FIRE_STAT_COUNT_MAX) fireCircular--;
            else fireHead--;
        }
    }

    public List<AimingData> getTurnAimDatas() {
        return turnAimDatas;
    }

    public void setTurnAimDatas(List<AimingData> turnAimDatas) {
        this.turnAimDatas = turnAimDatas;
    }

    public AimingData getBestAiming(Point2D.Double from, double gunHeadingRadians) {
        double maxhitrate = 0;
        AimingData aimingData = null;
        for (AimingData ad : turnAimDatas) {
            double hr = ad.getGunner().getEnemyRoundFireStat(this).getHitRate();
            double a = getPointAngle(from, ad.getFiringPosition());
            if ((hr > maxhitrate)) {//&& (abs(gunHeadingRadians - a) < GUN_TURN_RATE_RADIANS / 1.1)) {
                aimingData = ad;
                maxhitrate = hr;
            }
        }
        return aimingData;
    }
}

