package tankbase.enemy;

import robocode.Rules;
import robocode.ScannedRobotEvent;
import tankbase.*;
import tankbase.gun.Aiming;
import tankbase.gun.kdformula.KDFormula;
import tankbase.gun.kdformula.Pattern;
import tankbase.gun.kdformula.Surfer;
import tankbase.gun.log.FireLog;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.min;
import static java.lang.Math.signum;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MIN_BULLET_POWER;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.AbstractTankBase.*;
import static tankbase.Constant.*;
import static tankbase.TankUtils.*;
import static tankbase.WaveLog.logWave;
import static tankbase.enemy.EnemyDB.filterEnemies;


public class Enemy implements ITank {
    public static final int MAX_GUN_HEAT = 3;

    private final String name;
    private final LinkedList<Move> moveLog = new LinkedList<>();
    private final Pattern pattern;
    private final Surfer surfer;
    int fireHead = 1;
    int fireCircular = 0;
    int FIRE_STAT_COUNT_MAX = 3;
    private TankState state;
    private TankState prevState;
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
        state = null;
        alive = false;
        pattern = new Pattern(this);
        surfer = new Surfer(this, tankBase);
        update(sre, tankBase);
    }

    public void update(ScannedRobotEvent sre, AbstractTankBase tankBase) {
        this.tankBase = tankBase;
        prevState = state;
        state = new TankState(sre, prevState, tankBase.getState());
        lastScan = state.getTime();

        computeFEnergy();

        if (prevScannedTankState != null) {
            checkEnemyFire();
            if (state.getVelocity() == 0 || signum(prevScannedTankState.getVelocity()) != signum(state.getVelocity()))
                lastStop = state.getTime();

            if (state.getTurnRate() == 0 || signum(prevScannedTankState.getTurnRate()) != signum(state.getTurnRate()))
                lastChangeDirection = state.getTime();

            lastVelocityChange = (state.getAcceleration() == 0) ? lastVelocityChange : state.getTime();

            if (prevScannedTankState != null) {
                long deltaTime = state.getTime() - prevScannedTankState.getTime();
                double distance = state.distance(prevScannedTankState);
                double turn = state.getHeadingRadians() - prevScannedTankState.getHeadingRadians();
                moveLog.add(new Move(pattern.getPoint(prevState), surfer.getPoint(prevState), turn, distance * signum(state.getVelocity()), deltaTime));
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

        prevScannedTankState = state;
        lastScan = state.getTime();
        scanned = true;
        alive = true;
    }

    void computeFEnergy() {
        fEnergy = state.getEnergy();
        FireLog.getFireLog(name).forEach(a -> fEnergy -= a.getDamage());
    }

    public void move() {
        TankState newState = state.extrapolateNextState();
        if (newState != null) {
            if (newState.getTime() <= tankBase.getTime()) {
                prevState = state;
                state = newState;
            }

            if (state.getTime() - lastScan > MAX_NOT_SCAN_TIME)
                scanned = false;
        }
    }

    public void reset() {
        alive = false;
        scanned = false;
        prevScannedTankState = prevState = state = null;
    }

    public void die() {
        scanned = alive = false;
    }

    private void checkEnemyFire() {
        if (prevState == null || state.getGunHeat() > 0 || !scanned)
            return;

        double drop = prevState.getEnergy() - state.getEnergy();
        if (drop < MIN_BULLET_POWER || drop > MAX_BULLET_POWER)
            return;

        state.setGunHeat(Rules.getGunHeat(drop));
        long waveStart = prevState.getTime() + (long) (prevState.getGunHeat() / GUN_COOLING_RATE);
        Wave w = new Wave(tankBase, drop, waveStart, this, fireHead, fireCircular);

        logWave(w);
    }

    // Getters
    @Override
    public TankState getState() {
        return state;
    }

    @Override
    public String getName() {
        return name;
    }

    public long getLastScan() {
        return lastScan;
    }

    public double getAngle() {
        return normalAbsoluteAngle(getPointAngle(tankBase.getState(), state));
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

    public long getLastUpdateDelta(long now) {
        return tankBase.getTime() - now;
    }

    public long getLastScanDelta() {
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
        return min(state.getX(), FIELD_WIDTH - state.getX());
    }

    public double getWallDistanceY() {
        return min(state.getY(), FIELD_HEIGHT - state.getY());
    }

    public double getWallDistance() {
        return min(getWallDistanceX(), getWallDistanceY());
    }

    public double getForwardWallDistance() {
        return tankBase.getState().distance(wallIntersection(state, state.getMovingDirection()));
    }

    public double getDanger(int x, int y, double maxDamageMe) {
        if (!scanned) return 0;
        double scale = FieldMap.getScale();
        Point2D.Double p = new Point2D.Double(x * scale + scale / 2, y * scale + scale / 2);
        double d = state.distance(p);
        if (!isMaxDanger(x, y)) {
            boolean shadowed = filterEnemies(e -> e.isAlive() && e != this).stream()
                    .map(e -> collisionCircleSegment(e.getState(), TANK_SIZE, p, state))
                    .reduce((a, b) -> a || b)
                    .orElse(false);

            if (shadowed)
                return 0;

            double danger = Math.pow((DISTANCE_MAX - d + MAX_DANGER_RADIUS) / DISTANCE_MAX, 8);
            return danger * (damageMe + 10) / (maxDamageMe + 10);
        }
        return 1;
    }

    public boolean isMaxDanger(int x, int y) {
        double scale = FieldMap.getScale();
        Point2D.Double p = new Point2D.Double(x * scale + scale / 2, y * scale + scale / 2);
        double d = state.distance(p);
        return d <= MAX_DANGER_RADIUS;

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
        return String.format("Enemy %s alive=%b fErnergy=%.1f, damageMe=%.1f %s", name, alive, fEnergy, damageMe, state);
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
