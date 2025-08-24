package tankbase;

import robocode.Rules;
import robocode.ScannedRobotEvent;
import tankbase.gun.AimingData;
import tankbase.gun.Shell;
import tankbase.gun.kdFormula.KDFormula;
import tankbase.gun.kdFormula.Pattern;
import tankbase.gun.kdFormula.Surfer;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.signum;
import static java.lang.Math.sqrt;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MIN_BULLET_POWER;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.AbstractTankBase.DANGER_SCALE;
import static tankbase.AbstractTankBase.DISTANCE_MAX;
import static tankbase.AbstractTankBase.FIELD_HEIGHT;
import static tankbase.AbstractTankBase.FIELD_WIDTH;
import static tankbase.AbstractTankBase.GUN_COOLING_RATE;
import static tankbase.AbstractTankBase.MAX_DANGER_RADIUS;
import static tankbase.AbstractTankBase.TANK_SIZE;
import static tankbase.AbstractTankBase.getEnemys;
import static tankbase.AbstractTankBase.sysout;
import static tankbase.TankUtils.collisionCercleSeg;
import static tankbase.TankUtils.getPointAngle;
import static tankbase.TankUtils.wallIntersection;


public class Enemy implements ITank {
    public static final int MAX_GUN_HEAT = 3;

    private final String name;

    private TankState tankState;
    private TankState prevTankState;
    private AbstractTankBase tankBase;
    private Pattern pattern;
    private Surfer surfer;
    private List<AimingData> turnAimDatas = new ArrayList<>();

    private long lastScan;
    private long lastStop;
    private long lastChangeDirection;
    private long lastVelocityChange;
    private Boolean alive = false;
    private final List<Move> moveLog = new LinkedList<Move>();
    private int hitMe = 0;
    private double damageMe = 0;
    private double fEnergy;

    int fireHead = 1;
    int fireCircular = 0;

    public Enemy(ScannedRobotEvent sre, String name, AbstractTankBase tankBase, ArrayList<Wave> waves) {
        this.name = name;
        tankState = null;
        alive = false;
        pattern = new Pattern(this);
        surfer = new Surfer(this, tankBase);
        update(sre, tankBase, waves);
    }

    public void update(ScannedRobotEvent sre, AbstractTankBase tankBase, ArrayList<Wave> waves) {
        //sysout.printf("Updating enemy %s, alive %b\n", sre.getName(), alive);
        this.tankBase = tankBase;
        if (alive && !tankState.extrepolated)
            prevTankState = tankState;
        tankState = new TankState(sre, prevTankState, tankBase.getState());
        lastScan = tankBase.getTime();

        if (alive) {
            checkEnemyFire(waves);
            if (tankState.getVelocity() == 0 || signum(prevTankState.getVelocity()) != signum(tankState.getVelocity()))
                lastStop = tankState.getTime();

            if (tankState.getTurnRate() == 0 || signum(prevTankState.getTurnRate()) != signum(tankState.getTurnRate()))
                lastChangeDirection = tankState.getTime();

            lastVelocityChange = (tankState.getAcceleration() == 0) ? 0 : (lastVelocityChange == 0 ? tankState.getTime() : lastVelocityChange);

            if (prevTankState != null) {
                long deltaTime = tankState.getTime() - prevTankState.getTime();
                double distance = tankState.getPosition().distance(prevTankState.getPosition());
                double turn = tankState.getHeadingRadians() - prevTankState.getHeadingRadians();
                moveLog.add(new Move(pattern.getPoint(), surfer.getPoint(), turn, distance*signum(tankState.getVelocity()), deltaTime));
            }

            if (moveLog.size() > tankBase.aimingMoveLogSize) {
                List<Move> log = new ArrayList<>(moveLog.subList(moveLog.size() - tankBase.aimingMoveLogSize, moveLog.size()));
                Move m = log.get(0);
                pattern.addPoint(m.getPatternKdPoint(), log);
                surfer.addPoint(m.getSurferKdPoint(), log);
                moveLog.remove(0);
            }
        } else
            fEnergy = tankState.getEnergy();

        //sysout.printf("Enemy %s updated: %s\n", name, tankState);

        alive = true;
    }

    public void move(long iteration) {
        if (prevTankState == null && !tankState.extrepolated)
            prevTankState = tankState;

        for (long i = 0; i < iteration; i++) {
            TankState newState = tankState.extrapolateNextState();
            if (newState != null) tankState = newState;
        }
    }

    public void die() {
        alive = false;
        prevTankState = tankState = null;
    }

    private void checkEnemyFire(ArrayList<Wave> waves) {
        if (prevTankState == null)
            return;

        double drop = prevTankState.getEnergy() - tankState.getEnergy();
        if (drop < 0 || drop < MIN_BULLET_POWER || drop > MAX_BULLET_POWER || tankState.getGunHeat()>0)
            return;

        tankState.setGunHeat(Rules.getGunHeat(drop));
        long waveStart = prevTankState.getTime() + (long) (prevTankState.getGunHeat()/GUN_COOLING_RATE);
        Wave w = new Wave(tankBase, drop, waveStart, this, fireHead, fireCircular);
        waves.add(w);
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

    public void setEnergy(double energy, boolean updateFenergy) {
        if (updateFenergy) {
            double delta = tankState.getEnergy() - energy;
            fEnergy -= delta;
        }
        tankState.setEnergy(energy);
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
        return tankState.getTime() - lastScan;
    }

    public KDFormula getPatternFormula() {
        return pattern;
    }

    public KDFormula getSurferFormula() {
        return surfer;
    }
    public AbstractTankBase getGpBase() {
        return tankBase;
    }

    @Override
    public List<Move> getMoveLog() {
        return moveLog;
    }

    public double getWallDistance() {
        return sqrt(pow(getWallDistanceX(), 2) + pow(getWallDistanceY(), 2));
    }

    public double getForwardWallDistance() {
        return tankBase.getPosition().distance(wallIntersection(tankState.getPosition(), tankState.getMovingDirection()));
    }

    public double getWallDistanceX() {
        return min(tankState.getX(), FIELD_WIDTH - tankState.getX());
    }

    public double getWallDistanceY() {
        return min(tankState.getY(), FIELD_HEIGHT - tankState.getY());
    }

    public double getClosestWallDistance() {
        return min(getWallDistanceX(), getWallDistanceY());
    }

    public double getDanger(int x, int y, double maxDamageMe) {
        Point2D.Double p = new Point2D.Double(x, y);
        Point2D.Double pos = getState().getPosition();
        double d = pos.distance(x*DANGER_SCALE, y*DANGER_SCALE);
        if (d > MAX_DANGER_RADIUS) {
            boolean shadowed = getEnemys()
                    .filter(Enemy::isAlive)
                    .filter(e -> e != this)
                    .map(e -> collisionCercleSeg(e.tankState.getPosition(), TANK_SIZE,
                                                 new Point2D.Double(x*DANGER_SCALE, y*DANGER_SCALE), pos))
                    .reduce((a, b) -> a||b)
                    .orElse(false);

            if (shadowed)
                return 0;

            double danger = Math.pow((DISTANCE_MAX - d+ MAX_DANGER_RADIUS) / DISTANCE_MAX, 8);
            return danger * (getDamageMe() + .001) / (maxDamageMe + .001);
        }
        return 1;
    }

    public List<Shell> getFireLog(String target) {
        return new ArrayList<>();
    }

    int FIRE_STAT_COUNT_MAX = 3;

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
            if ((hr > maxhitrate) || aimingData == null) {//&& (abs(gunHeadingRadians - a) < GUN_TURN_RATE_RADIANS / 1.1)) {
                aimingData = ad;
                maxhitrate = hr;
            }
        }
        return aimingData;
    }
}
