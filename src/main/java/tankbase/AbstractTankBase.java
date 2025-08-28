package tankbase;

import robocode.Bullet;
import robocode.BulletHitBulletEvent;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.DeathEvent;
import robocode.Event;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.RobotDeathEvent;
import robocode.RoundEndedEvent;
import robocode.ScannedRobotEvent;
import robocode.SkippedTurnEvent;
import tankbase.enemy.Enemy;
import tankbase.gun.Aiming;
import tankbase.gun.CircularGunner;
import tankbase.gun.Fire;
import tankbase.gun.FireStat;
import tankbase.gun.Gunner;
import tankbase.gun.HeadOnGunner;
import tankbase.gun.PatternGunner;
import tankbase.gun.SurferGunner;
import tankbase.gun.log.FireLog;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.Math.*;
import static robocode.Rules.GUN_TURN_RATE_RADIANS;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MIN_BULLET_POWER;
import static robocode.Rules.getBulletDamage;
import static robocode.Rules.getBulletSpeed;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.Constant.MAX_NOT_SCAN_TIME;
import static tankbase.enemy.EnemyDB.*;
import static tankbase.FieldMap.computeDangerMap;
import static tankbase.gun.log.FireLog.clearFireLog;
import static tankbase.gun.log.FireLog.getFireByDirection;
import static tankbase.gun.log.FireLog.logFire;
import static tankbase.gun.log.FireLog.removeFire;
import static tankbase.TankUtils.computeTurnGun2Target;
import static tankbase.TankUtils.computeTurnGun2TargetNextPos;
import static tankbase.TankUtils.getPointAngle;
import static tankbase.TankUtils.oppositeAngle;
import static tankbase.TankUtils.trigoAngle;
import static tankbase.gun.log.VirtualFireLog.clearVirtualFireLog;
import static tankbase.gun.log.VirtualFireLog.logVirtualFire;
import static tankbase.gun.log.VirtualFireLog.updateVirtualFires;
import static tankbase.WaveLog.clearWaveLog;
import static tankbase.WaveLog.getWave;
import static tankbase.WaveLog.removeWave;
import static tankbase.WaveLog.updateWaves;

abstract public class AbstractTankBase extends AbstractCachedTankBase implements ITank {

    public static double FIELD_WIDTH;
    public static double FIELD_HEIGHT;
    public static Point2D.Double BATTLE_FIELD_CENTER;
    public static double DISTANCE_MAX;
    public static double GUN_COOLING_RATE;
    public static PrintStream sysout;
    public int moveLogMaxSize;


    private static final Map<String, Gunner> gunners = new HashMap<>();

    private static FireStat gpStat;
    private static HeadOnGunner headOnGunner = null;

    private Enemy prevTarget = null;
    public Enemy target;
    protected Enemy mostLeft;
    protected Enemy mostRight;

    protected Aiming aiming = null;

    private int aliveCount;

    public Point2D.Double destination;

    public double scandirection = 1;
    public double forward = 1;
    public double turnLeft = 0;
    public double turnGunLeft = 0;
    public double turnRadarLeft = 0;
    public double ahead = 0;
    public double firePower = 0;
    private boolean alive;

    protected AbstractTankBase() {
        super();
        setupGunners();
        gpStat = new FireStat();
    }

    /// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Static methods

    public static void putGunner(Gunner gunner) {
        gunners.put(gunner.getName(), gunner);
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GPBase logic

    // GP Robot overide this method
    public void doGP() {
    }

    public void doTurn() {
        doUpdate();

        computeSafePosition();
        computeAiming();
        selectTarget();
        fireVirtualShell();
        checks();

        turnRadarLeft = getTurnRadar();
        turnLeft = getTurn();
        ahead = getAhead();
        turnGunLeft = getTurnGun();
        firePower = (aiming == null) ? 0 : aiming.getFirePower();

        if (aiming != null) {
            doGP();
        }

        robotSetActions();
    }

    private void doUpdate() {
        updateRobotCache();
        updateEnemies();
        updateWaves(getState(), getTime());
        updateVirtualFires(getTime());
        updateDangerMap();
        aliveCount = getAliveCount();
        aiming = null;
    }

    private double getTurnRadar() {
        int oc = getOthers();
        if (aliveCount == oc && oc > 0) {
            long lastupdateDelta = updateLeftRightEnemies();
            if (lastupdateDelta > MAX_NOT_SCAN_TIME) return 2 * PI;
            double ra = normalAbsoluteAngle(trigoAngle(getRadarHeadingRadians()));
            return scanLeftRight(ra, mostLeft.getAngle(), mostRight.getAngle());
        }
        return 2 * PI;
    }

    private void robotSetActions() {
        setTurnRadarLeftRadians(turnRadarLeft);
        setTurnLeftRadians(turnLeft);
        setAhead(ahead);
        setTurnGunLeftRadians(turnGunLeft);
        fireTargetIfPossible();
    }

    private double scanLeftRight(double ra, double ml, double mr) {
        if ((ra >= ml && ra < ml + 2 * Constant.SCAN_OFFSET)
                || (ra < 2 * Constant.SCAN_OFFSET + ml - 2 * PI && ml > 2 * PI - 2 * Constant.SCAN_OFFSET))
            scandirection = -1;
        else if ((ra < mr && ra > mr - 2 * Constant.SCAN_OFFSET) || (ra > 2 * PI - 2 * Constant.SCAN_OFFSET + mr && mr < 2 * Constant.SCAN_OFFSET))
            scandirection = 1;

        if (scandirection == 1)
            return ml + Constant.SCAN_OFFSET - ((ml >= ra) ? ra : ra - 2 * PI);
        else
            return mr - Constant.SCAN_OFFSET - ((mr <= ra) ? ra : ra + 2 * PI);
    }

    private void updateDangerMap() {
        computeDangerMap(listEnemies(), getEmenmiesMaxDamageMe(), getTime());
    }

    private long updateLeftRightEnemies() {
        List<Enemy> sEnemies = filterAndSortEnemies(Enemy::isAlive, (e1, e2) -> {
            double a1 = e1.getAngle();
            double a2 = e2.getAngle();
            return Double.compare(a1, a2);
        });

        Enemy prev = mostRight = sEnemies.get(0);
        mostLeft = sEnemies.get(sEnemies.size() - 1);
        double ba = abs(mostLeft.getAngle() - mostRight.getAngle());
        if (ba > PI) ba = 2 * PI - ba;
        long lastUpdateDelta = 0;
        for (Enemy enemy : sEnemies) {
            final double a = abs(enemy.getAngle() - prev.getAngle());
            if (a > ba) {
                mostRight = enemy;
                mostLeft = prev;
                ba = a;
            }
            prev = enemy;
            lastUpdateDelta = max(lastUpdateDelta, enemy.getLastScanDelta());
        }
        return lastUpdateDelta;
    }

    private void selectTarget() {
        prevTarget = target;
        Enemy newTarget = null;
        double minDistance = Double.POSITIVE_INFINITY;
        double prevTurnGun = 2 * PI;

        for (Enemy e : listEnemies()) {
            if (!e.isAlive() || !e.isScanned() || e.getTurnAimDatas().isEmpty()) continue;
            if (e.getFEnergy() < 0) continue;

            double distance = getState().distance(e.getState());
            double heading = getPointAngle(getState(), e.getState());
            double gunTurn = heading - getGunHeadingRadians();

            if (e.getState().getEnergy() == 0 && abs(gunTurn) < GUN_TURN_RATE_RADIANS) {
                newTarget = e;
                break;
            }
            if (distance > minDistance) continue;

            // new target should be 2/3 closer than previous to avoid target switch to often
            if (prevTarget != null && prevTarget.isAlive() && prevTarget.getFEnergy() > 0 &&
                    distance > prevTarget.getState().distance(getState()) * 2 / 3 && abs(gunTurn) > GUN_TURN_RATE_RADIANS)
                continue;

            newTarget = e;
            minDistance = distance;
        }

        // If no aiming available just pick an alive target and turn gun to him
        if (newTarget == null && aliveCount > 0)
            newTarget = getCloseAliveEnemy(getState());

        target = newTarget;

        if (target != null && target.isAlive()) {
            if (target.getState().getEnergy() == 0)
                aiming = headOnGunner.aim(target);
            else
                aiming = target.getBestAiming();
        }
    }

    private void computeAiming() {
        filterEnemies(Enemy::isAlive).forEach(e-> computeAimingTarget(e));
    }

    private void computeAimingTarget(Enemy target) {
        ArrayList<Aiming> aimings = new ArrayList<>();
        for (Gunner gunner : gunners.values()) {
            Aiming ad = gunner.aim(target);
            if (ad != null) {
                aimings.add(ad);
            }
        }
        target.setTurnAimDatas(aimings);
    }

    private void updateEnemies() {
        filterEnemies(e -> e.isAlive() && (e.getState().getEnergy() > 0))
                .forEach(Enemy::move);
    }

    public double getEmenmiesMaxDamageMe() {
        return listEnemies().stream().filter(Enemy::isAlive).map(Enemy::getDamageMe)
                .max(Double::compare).orElse(0.0);
    }

    private void computeSafePosition() {
        destination = FieldMap.computeSafePosition(getState(), listEnemies());
    }

    private double getTurn() {
        if (destination == null)
            return 0;

        double sa = getPointAngle(getState(), destination);
        double ra = getHeadingRadians();

        if (abs(normalRelativeAngle(sa - ra)) <= (PI / 2)) {
            forward = 1;
            return normalRelativeAngle(sa - ra);
        }

        forward = -1;
        return normalRelativeAngle(oppositeAngle(sa) - ra);
    }

    private double getAhead() {
        if (destination == null || getState() == null)
            return 0;

        return forward * destination.distance(getState());
    }

    private void fireTargetIfPossible() {
        if (getGunHeat() > 0 || firePower == 0 || target == null || !target.isAlive() || aiming == null ||
                (target.getFEnergy() < 0 && target.getTurnAimDatas().isEmpty())) {
            return;
        }

        double a = getPointAngle(getState(), aiming.getFiringPosition());
        if (getState().distance(aiming.getFiringPosition()) * abs(tan(getGunHeadingRadians() - a)) >= Constant.FIRE_TOLERANCE) {
            //out.printf("Fire on %s rejected by tolerance, turn remaining=%.02f,  offset=%.02f\n", target.getName(), getTurnGun(), getGunHeadingRadians()-a);
            return;
        }

        /*out.printf("aiming: %s->%s at x=%f y=%f \n",
                aimingData.getGunner().getName(), aimingData.getTarget().getName(),
                aimingData.getFiringPosition().getX(),
                aimingData.getFiringPosition().getY());*/

        firePower = aiming.getFirePower();
        setFire(firePower);
        target.addFEnergy(-getBulletDamage(firePower));
        aiming.setDirection(getGunHeadingRadians());
        //out.printf("%s fire on %s, damage=%.02f, power=%.02f\n", aimingData.getGunner().getName(), target.getName(), getBulletDamage(fire), fire);
        logFire(new Fire(getState(), aiming, getTime()));
    }

    private void checks() {
        if (aiming != null &&
                target != null && target.isAlive() &&
                getGunHeat() == 0 &&
                aliveCount == 1 &&
                target.getLastChangeDirection() < 5 &&
                abs(getPointAngle(getState(), aiming.getFiringPosition())) > GUN_TURN_RATE_RADIANS) {
            // In duel, some tanks are able to deny fire by changing direction fast, so we use head on to stop him change direction
            aiming = new Aiming(headOnGunner, target, MIN_BULLET_POWER);
            //out.printf("Switch to head on on %s to avoid erratic aiming\n", target.getName());
        }
    }

    private void fireVirtualShell() {
        filterEnemies(Enemy::isAlive).forEach(e -> {
            e.getTurnAimDatas().forEach(ad -> {
                //out.printf("new Shell to %.0f, %.0f\n", ad.getFiringPosition().getX(), ad.getFiringPosition().getY());
                logVirtualFire(new Fire(getState(), ad, getTime()));
            });
        });
    }

    private double getTurnGun() {
        if (target == null || !target.isAlive())
            return 0;

        if (aiming == null || getGunHeat() != 0 && aliveCount == 1) {
            TankState targetState = target.getState().extrapolateNextState();
            if (targetState != null)
                return computeTurnGun2TargetNextPos(this, targetState);
            return computeTurnGun2TargetNextPos(this, target.getState());
        }

        /*out.printf("Fire on %s at [%.0f,%.0f], %s head=%.0f, gun2FirePos head=%.0f, turn=%.0f \n", target.getName(),
                   aimingData.getFiringPosition().getX(), aimingData.getFiringPosition().getY(),
                   aimingData.getGunner().getName(),
                   toDegrees(getGunHeadingRadians()),
                   toDegrees(getPointAngle(getNextPosition(), aimingData.getFiringPosition())),
                   toDegrees(computeTurnGun2Target(getNextPosition(), aimingData.getFiringPosition(), getGunHeadingRadians())));
        */
        return computeTurnGun2Target(getNextPosition(), aiming.getFiringPosition(), getGunHeadingRadians());
    }

    public int getAliveCount() {
        return (int) listEnemies().stream().filter(Enemy::isAlive).count();
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    /// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // AdvancedRobot overrides
    @Override
    public void run() {
        AbstractTankBase.sysout = out;

        FIELD_WIDTH = (int) getBattleFieldWidth();
        FIELD_HEIGHT = (int) getBattleFieldHeight();
        BATTLE_FIELD_CENTER = new Point2D.Double(FIELD_WIDTH / 2, FIELD_HEIGHT / 2);
        GUN_COOLING_RATE = getGunCoolingRate();
        DISTANCE_MAX = new Point2D.Double(0, 0).distance(FIELD_WIDTH, FIELD_HEIGHT);

        FieldMap.initFieldMap();
        moveLogMaxSize = (int) (DISTANCE_MAX / getBulletSpeed(MAX_BULLET_POWER) + 1);

        updateRobotCache();
        aliveCount = super.getOthers();
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setColors(Color.red, Color.blue, Color.green);

        listEnemies().forEach(Enemy::reset);
        updateGunners();
        alive = true;

        while (true) {
            doTurn();
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        onEvent(e);
        String name = e.getName();
        Enemy enemy = getEnemy(name);

        if (enemy == null)
            addEnemy(new Enemy(e, name, this));
        else
            enemy.update(e, this);
    }

    @Override
    public void onBulletHit(BulletHitEvent bhe) {
        onEvent(bhe);
        gpStat.hit(bhe.getEnergy());
        Optional<Fire> of = getFireByDirection(trigoAngle(bhe.getBullet().getHeadingRadians()));
        of.ifPresent(FireLog::removeFire);
    }

    @Override
    public void onBulletMissed(BulletMissedEvent bme) {
        onEvent(bme);
        Optional<Fire> of = getFireByDirection(trigoAngle(bme.getBullet().getHeadingRadians()));
        of.ifPresent(fire -> {
            fire.getTarget().addFEnergy(getBulletDamage(bme.getBullet().getPower()));
            removeFire(fire);
        });
    }

    @Override
    public void onBulletHitBullet(BulletHitBulletEvent bhbe) {
        onEvent(bhbe);
        Bullet b = bhbe.getHitBullet();
        Point2D.Double p = new Point2D.Double(b.getX(), b.getY());
        Optional<Wave> ow = getWave(b.getName(), p, getTime());
        ow.ifPresent(WaveLog::removeWave);

        Optional<Fire> of = getFireByDirection(trigoAngle(bhbe.getBullet().getHeadingRadians()));
        of.ifPresent(fire -> {
            removeFire(fire);
            fire.getTarget().addFEnergy(getBulletDamage(bhbe.getBullet().getPower()));
        });
    }

    @Override
    public void onHitByBullet(HitByBulletEvent hbbe) {
        onEvent(hbbe);
        Enemy e = getEnemy(hbbe.getName());
        if (e == null) return;

        e.damageMe(getBulletDamage(hbbe.getPower()));

        Bullet b =hbbe.getBullet();
        Point2D.Double p = new Point2D.Double(b.getX(), b.getY());
        Optional<Wave> ow = getWave(hbbe.getName(), p, getTime());
        ow.ifPresent(wave -> {
            double bulletHeading = trigoAngle(hbbe.getHeadingRadians());
            double headOn = getPointAngle(wave, wave.head);
            double circular = getPointAngle(wave, wave.circular);

            if (abs(headOn - bulletHeading) < abs(circular - bulletHeading))
                e.fireHead();
            else
                e.fireCircular();
            removeWave(wave);
        });
    }

    @Override
    public void onDeath(DeathEvent event) {
        onEvent(event);
        alive = false;
    }

    @Override
    public void onRoundEnded(RoundEndedEvent event) {
        onEvent(event);
        resetRoundDataPrintStat();
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        onEvent(event);
        Enemy enemy = getEnemy(event.getName());
        if (enemy != null) // If robot die before we scan it
            enemy.reset();
    }

    private void onEvent(Event e) {
        updateRobotCache(e.getTime());
    }

    @Override
    public void onSkippedTurn(SkippedTurnEvent event) {
        out.printf("Skip turn: %d %d%n", event.getSkippedTurn(), event.getPriority());
    }

    @Override
    public void onHitRobot(HitRobotEvent e) {
        // Try escape backward and turn PI/2
        //forward *= -1;
        //setTurnLeftRadians(PI / 2);
        //setAhead(forward * TANK_SIZE * 2);
    }

    private void resetRoundDataPrintStat() {
        printStat();
        clearFireLog();
        clearVirtualFireLog();
        clearWaveLog();
        mostLeft = mostRight = null;
        target = prevTarget = null;
        listEnemies().forEach(Enemy::reset);
        updateGunners();
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////
    // Utils
    private void resetRoundStat() {
        gunners.values().forEach(gunner -> gunner.resetRoundStat());
    }

    private void printStat() {
        gunners.values().forEach(gunner -> {
            out.printf("==== %s ====%n", gunner.getName());
            listEnemies().forEach(enemy -> {
                FireStat fs = gunner.getEnemyRoundFireStat(enemy);
                out.printf("    %s hitrate = %.0f%% / %d, dmg/cost=%.0f%%%n", enemy.getName(), fs.getHitRate() * 100, fs.getFireCount(),
                           fs.getDommageCostRatio() * 100);
            });
        });
        resetRoundStat();
    }

    public Point2D.Double getNextPosition() {
        TankState next = getState().extrapolateNextState();
        if (next != null)
            return next;
        return getState();
    }

    private void setupGunners() {
        if (gunners.size() == 0) {
            headOnGunner = new HeadOnGunner(this);
            putGunner(new CircularGunner(this));
            putGunner(new PatternGunner(this));
            putGunner(new SurferGunner(this));
        }
    }

    private void updateGunners() {
        headOnGunner.setGunner(this);
        gunners.values().forEach(gunner -> gunner.setGunner(this));
    }
}