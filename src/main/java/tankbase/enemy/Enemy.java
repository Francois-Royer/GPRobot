package tankbase.enemy;

import robocode.Rules;
import tankbase.AbstractTankBase;
import tankbase.FieldMap;
import tankbase.ITank;
import tankbase.KDMove;
import tankbase.Move;
import tankbase.TankState;
import tankbase.gun.Aiming;
import tankbase.gun.kdformula.AntiSurfer;
import tankbase.gun.kdformula.Cluster;
import tankbase.gun.kdformula.KDFormula;
import tankbase.wave.Wave;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.signum;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MIN_BULLET_POWER;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.AbstractTankBase.GUN_COOLING_RATE;
import static tankbase.AbstractTankBase.sysout;
import static tankbase.AbstractTankDrawingBase.INFO_LEVEL;
import static tankbase.Constant.TANK_MAX_DANGER_RADIUS;
import static tankbase.Constant.TANK_SIZE;
import static tankbase.TankUtils.collisionCircleSegment;
import static tankbase.TankUtils.getPointAngle;
import static tankbase.TankUtils.wallIntersection;
import static tankbase.enemy.EnemyDB.filterEnemies;
import static tankbase.gun.log.FireLog.getFireLog;
import static tankbase.wave.WaveLog.logWave;


public class Enemy implements ITank {
    public static final int MAX_GUN_HEAT = 3;

    private final String name;
    private final LinkedList<KDMove> kDMoveLog = new LinkedList<>();
    private final Cluster cluster;
    private final AntiSurfer antiSurfer;
    double fireAngleRatio = 0;
    private TankState state;
    private TankState prevState;
    private TankState prevScannedState;
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

    public Enemy(EnemyDetectedEvent ede, String name, AbstractTankBase tankBase) {
        this.name = name;
        cluster = new Cluster(this, tankBase);
        antiSurfer = new AntiSurfer(this, tankBase);
        reset();
        update(ede, tankBase);
    }

    public void update(EnemyDetectedEvent ede, AbstractTankBase tankBase) {
        if (!alive) {
            // Scan event after RobotDeath event???
            return;
        }

        this.tankBase = tankBase;
        prevState = state;
        state = new TankState(ede, prevState, tankBase.getState());
        lastScan = state.getTime();


        computeFEnergy();

        if (prevScannedState != null) {
            checkEnemyFire();
            if (state.getVelocity() == 0 && prevScannedState.getVelocity() != 0 ||
                    state.getVelocity() != 0 && prevScannedState.getVelocity() != 0 && signum(prevScannedState.getVelocity()) != signum(state.getVelocity()))
                lastStop = state.getTime();

            if (state.getTurnRate() == 0 || signum(prevScannedState.getTurnRate()) != signum(state.getTurnRate()))
                lastChangeDirection = state.getTime();

            lastVelocityChange = (state.getAcceleration() == 0) ? lastVelocityChange : state.getTime();

            long deltaTime = state.getTime() - prevScannedState.getTime();
            double distance = state.distance(prevScannedState);
            double turn = state.getHeadingRadians() - prevScannedState.getHeadingRadians();
            kDMoveLog.add(new KDMove(cluster.getPoint(), antiSurfer.getPoint(), turn, distance * signum(state.getVelocity()), deltaTime));

            if (kDMoveLog.size() > tankBase.moveLogMaxSize) {
                List<KDMove> log = new ArrayList<>(kDMoveLog.subList(0, tankBase.moveLogMaxSize));
                KDMove m = log.get(0);
                List<Move> mLog = log.stream().map(KDMove::getMove).toList();
                cluster.addPoint(m.getClusterKdPoint(), mLog);
                antiSurfer.addPoint(m.getAntiSurferKdPoint(), mLog);
                kDMoveLog.removeFirst();
            }
        }

        prevScannedState = state;
        if (INFO_LEVEL > 2) {
            sysout.printf("%s scanned %s%n", name, state);
        }
    }

    void computeFEnergy() {
        fEnergy = state.getEnergy();
        getFireLog(name).forEach(a -> fEnergy -= a.getDamage());
    }

    public void move() {
        if (state == null || state.getTime() + 1 < tankBase.getTime()) return;
        TankState newState = state.extrapolateNextState();
        if (newState.getTime() <= tankBase.getTime() && newState.getTime() > state.getTime()) {
            prevState = state;
            state = newState;
        }
    }

    public void reset() {
        alive = true;
        state = prevScannedState = prevState = null;
    }

    public void die() {
        alive = false;
    }

    private void checkEnemyFire() {
        if (state.getGunHeat() > GUN_COOLING_RATE || prevScannedState == null)
            return;

        double drop = prevScannedState.getEnergy() - state.getEnergy();
        //sysout.printf("drop=%.02f\n",drop);
        if (drop < MIN_BULLET_POWER || drop > MAX_BULLET_POWER)
            return;

        state.setGunHeat(Rules.getGunHeat(drop));
        long waveStart = prevState.getTime() + (long) (prevState.getGunHeat() / GUN_COOLING_RATE);
        Wave w = new Wave(tankBase, drop, waveStart, this, fireAngleRatio);
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
        return prevScannedState != null && alive;
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

    public long getLastScanDelta() {
        return tankBase.getTime() - lastScan;
    }

    public KDFormula getPatternFormula() {
        return cluster;
    }

    public KDFormula getSurferFormula() {
        return antiSurfer;
    }

    @Override
    public List<KDMove> getMoveLog() {
        return kDMoveLog;
    }

    public double getForwardWallDistance() {
        return tankBase.getState().distance(wallIntersection(state, state.getMovingDirection()));
    }

    public double getDanger(int x, int y, double maxDamageMe) {
        if (state == null) return 0;
        double scale = FieldMap.getScale();
        Point2D.Double p = new Point2D.Double(x * scale + scale / 2, y * scale + scale / 2);
        double d = state.distance(p);
        if (!isMaxDanger(x, y)) {
            boolean shadowed = filterEnemies(e -> e.isScanned() && e != this).stream()
                    .map(e -> collisionCircleSegment(e.getState(), TANK_SIZE, p, state))
                    .reduce((a, b) -> a || b)
                    .orElse(false);

            if (shadowed)
                return 0;

            double danger = Math.pow((DISTANCE_MAX - d + TANK_MAX_DANGER_RADIUS) / DISTANCE_MAX, 8);
            return danger * (damageMe + 10) / (maxDamageMe + 10);
        }
        return 1;
    }

    public boolean isMaxDanger(int x, int y) {
        double scale = FieldMap.getScale();
        Point2D.Double p = new Point2D.Double(x * scale + scale / 2, y * scale + scale / 2);
        double d = state.distance(p);
        return d <= TANK_MAX_DANGER_RADIUS;

    }

    public List<Aiming> getTurnAimDatas() {
        return turnAimDatas;
    }

    public void setTurnAimDatas(List<Aiming> turnAimDatas) {
        this.turnAimDatas = turnAimDatas;
    }

    public Aiming getBestAiming() {
        double maxHitRate = 0;
        Aiming aiming = null;
        for (Aiming ad : turnAimDatas) {
            double hr = ad.getGun().getEnemyRoundFireStat(this).getHitRate();
            if ((hr > maxHitRate) || aiming == null) {
                aiming = ad;
                maxHitRate = hr;
            }
        }
        return aiming;
    }

    public double getFireAngleRatio() {
        return fireAngleRatio;
    }

    public void setFireAngleRatio(double fireAngleRatio) {
        this.fireAngleRatio = fireAngleRatio;
    }

    @Override
    public String toString() {
        return String.format("Enemy %s alive=%b fEnergy=%.1f, damageMe=%.1f %s", name, alive, fEnergy, damageMe, state);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Enemy enemy = (Enemy) o;
        return name.equals(enemy.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
