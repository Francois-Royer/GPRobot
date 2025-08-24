package tankbase;

import robocode.Rules;
import robocode.ScannedRobotEvent;
import tankbase.gun.Aiming;
import tankbase.gun.kdformula.KDFormula;
import tankbase.gun.kdformula.Pattern;
import tankbase.gun.kdformula.Surfer;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.min;
import static java.lang.Math.signum;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MIN_BULLET_POWER;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.AbstractTankBase.FIELD_HEIGHT;
import static tankbase.AbstractTankBase.FIELD_WIDTH;
import static tankbase.AbstractTankBase.GUN_COOLING_RATE;
import static tankbase.AbstractTankBase.getEnemys;
import static tankbase.Constant.MAX_DANGER_RADIUS;
import static tankbase.Constant.MAX_NOT_SCAN_TIME;
import static tankbase.Constant.TANK_SIZE;
import static tankbase.TankUtils.collisionCircleSegment;
import static tankbase.TankUtils.getPointAngle;
import static tankbase.TankUtils.wallIntersection;
import static tankbase.WaveLog.logWave;


public class Enemy implements ITank {
    public static final int MAX_GUN_HEAT = 3;

    private final String name;
    private final LinkedList<Move> moveLog = new LinkedList<>();
    private final Pattern pattern;
    private final Surfer surfer;
    int fireHead = 1;
    int fireCircular = 0;
    int FIRE_STAT_COUNT_MAX = 3;
    private TankState tankState;
    private TankState prevTankState;
    private TankState prevScannedTankState;
    private AbstractTankBase tankBase;
    private List<Aiming> turnAimDatas = new ArrayList<>();
    private long lastScan;
    private long lastStop;
    private long lastChangeDirection;
    private long lastVelocityChange;
    private int hitMe = 0;
    private double damageMe = 0;
    private double fEnergy;
    private boolean alive = false;
    private boolean scanned = false;

    public Enemy(ScannedRobotEvent sre, String name, AbstractTankBase tankBase) {
        this.name = name;
        tankState = null;
        alive = false;
        pattern = new Pattern(this);
        surfer = new Surfer(this, tankBase);
        update(sre, tankBase);
    }

    public void update(ScannedRobotEvent sre, AbstractTankBase tankBase) {
        this.tankBase = tankBase;
        prevTankState = tankState;
        tankState = new TankState(sre, prevTankState, tankBase.getState());
        lastScan = tankBase.getTime();

        computeFEnergy();

        if (prevScannedTankState != null) {
            checkEnemyFire();
            if (tankState.getVelocity() == 0 || signum(prevScannedTankState.getVelocity()) != signum(tankState.getVelocity()))
                lastStop = tankState.getTime();

            if (tankState.getTurnRate() == 0 || signum(prevScannedTankState.getTurnRate()) != signum(tankState.getTurnRate()))
                lastChangeDirection = tankState.getTime();

            lastVelocityChange = (tankState.getAcceleration() == 0) ? lastVelocityChange : tankState.getTime();

            if (prevScannedTankState != null) {
                long deltaTime = tankState.getTime() - prevScannedTankState.getTime();
                double distance = tankState.getPosition().distance(prevScannedTankState.getPosition());
                double turn = tankState.getHeadingRadians() - prevScannedTankState.getHeadingRadians();
                moveLog.add(new Move(pattern.getPoint(prevTankState), surfer.getPoint(prevTankState), turn, distance * signum(tankState.getVelocity()), deltaTime));
            }

            if (moveLog.size() > tankBase.moveLogMaxSize) {
                List<Move> log = new ArrayList<>(moveLog.subList(moveLog.size() - tankBase.moveLogMaxSize, moveLog.size()));
                Move m = log.get(0);
                pattern.addPoint(m.getPatternKdPoint(), log);
                if (m.getSurferKdPoint() != null)
                    surfer.addPoint(m.getSurferKdPoint(), log);
                moveLog.removeFirst();
            }

        }

        alive = true;
        scanned = true;
        prevScannedTankState = tankState;
    }

    void computeFEnergy() {
        fEnergy = tankState.getEnergy();
        FireLog.getFireLog(name).forEach(a -> fEnergy -= a.getDamage());
    }

    public void move() {
        TankState newState = tankState.extrapolateNextState();
        if (newState != null) {
            if (newState.getTime() < tankBase.getTime()) {
                prevTankState = tankState;
                tankState = newState;
            }

            if (tankBase.getTime() - lastScan > MAX_NOT_SCAN_TIME)
                scanned = false;
        }
    }

    public void reset() {
        alive = false;
        prevScannedTankState = prevTankState = tankState = null;
    }

    private void checkEnemyFire() {
        if (prevTankState == null || tankState.getGunHeat() > 0 || !scanned)
            return;

        double drop = prevTankState.getEnergy() - tankState.getEnergy();
        if (drop < MIN_BULLET_POWER || drop > MAX_BULLET_POWER)
            return;

        tankState.setGunHeat(Rules.getGunHeat(drop));
        long waveStart = prevTankState.getTime() + (long) (prevTankState.getGunHeat() / GUN_COOLING_RATE);
        Wave w = new Wave(tankBase, drop, waveStart, this, fireHead, fireCircular);
        logWave(w);
    }

    // Getters
    @Override
    public TankState getState() {
        return tankState;
    }

    @Override
    public String getName() {
        return name;
    }

    public long getLastScan() {
        return lastScan;
    }

    public double getAngle() {
        return normalAbsoluteAngle(getPointAngle(tankBase.getState().getPosition(), tankState.getPosition()));
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    public boolean isScanned() {
        return scanned;
    }

    public double getFEnergy() {
        return fEnergy;
    }

    public void addFEnergy(double v) {
        fEnergy += v;
    }

    @Override
    public long getLastStop() {
        return lastStop;
    }

    @Override
    public long getLastChangeDirection() {
        return lastChangeDirection;
    }

    @Override
    public long getLastVelocityChange() {
        return lastVelocityChange;
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
        return tankBase.getTime() - lastScan;
    }

    public KDFormula getPatternFormula() {
        return pattern;
    }

    public KDFormula getSurferFormula() {
        return surfer;
    }

    @Override
    public List<Move> getMoveLog() {
        return moveLog;
    }

    public double getWallDistanceX() {
        return min(tankState.getX(), FIELD_WIDTH - tankState.getX());
    }

    public double getWallDistanceY() {
        return min(tankState.getY(), FIELD_HEIGHT - tankState.getY());
    }

    public double getWallDistance() {
        return min(getWallDistanceX(), getWallDistanceY());
    }

    public double getForwardWallDistance() {
        return tankBase.getPosition().distance(wallIntersection(tankState.getPosition(), tankState.getMovingDirection()));
    }

    public double getDanger(int x, int y, double maxDamageMe) {
        double scale = FieldMap.getScale();
        Point2D.Double p = new Point2D.Double(x * scale + scale / 2, y * scale + scale / 2);
        Point2D.Double pos = getState().getPosition();
        double d = pos.distance(p);
        if (d > MAX_DANGER_RADIUS) {
            boolean shadowed = getEnemys()
                    .filter(Enemy::isAlive)
                    .filter(e -> e != this)
                    .map(e -> collisionCircleSegment(e.tankState.getPosition(), TANK_SIZE, p, pos))
                    .reduce((a, b) -> a || b)
                    .orElse(false);

            if (shadowed)
                return 0;

            double danger = Math.pow((DISTANCE_MAX - d + MAX_DANGER_RADIUS) / DISTANCE_MAX, 4);
            return danger * (getDamageMe() + .001) / (maxDamageMe + .001);
        }
        return 1;
    }

    public void fireHead() {
        fireHead++;
        if (fireHead + fireCircular > FIRE_STAT_COUNT_MAX) {
            if (fireHead > FIRE_STAT_COUNT_MAX) fireHead--;
            else fireCircular--;
        }
    }

    public void fireCircular() {
        fireCircular++;
        if (fireHead + fireCircular > FIRE_STAT_COUNT_MAX) {
            if (fireCircular > FIRE_STAT_COUNT_MAX) fireCircular--;
            else fireHead--;
        }
    }

    public List<Aiming> getTurnAimDatas() {
        return turnAimDatas;
    }

    public void setTurnAimDatas(List<Aiming> turnAimDatas) {
        this.turnAimDatas = turnAimDatas;
    }

    public Aiming getBestAiming() {
        double maxhitrate = 0;
        Aiming aiming = null;
        for (Aiming ad : turnAimDatas) {
            double hr = ad.getGunner().getEnemyRoundFireStat(this).getHitRate();
            if ((hr > maxhitrate) || aiming == null) {
                aiming = ad;
                maxhitrate = hr;
            }
        }
        return aiming;
    }

    @Override
    public String toString() {
        return String.format("Enemy %s %s alive=%b fErnergy=%.2f", name, tankState, alive, fEnergy);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Enemy enemy = (Enemy) o;
        return name.equals(enemy.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        return result;
    }
}
