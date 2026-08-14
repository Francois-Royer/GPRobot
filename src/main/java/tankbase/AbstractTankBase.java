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
import tankbase.enemy.EnemyDetectedEvent;
import tankbase.gun.Aiming;
import tankbase.gun.AntiSurferGun;
import tankbase.gun.CircularGun;
import tankbase.gun.ClusterGun;
import tankbase.gun.Fire;
import tankbase.gun.FireStat;
import tankbase.gun.Gun;
import tankbase.gun.HeadOnGun;
import tankbase.wave.Wave;
import tankbase.wave.WaveLog;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.tan;
import static java.util.function.Predicate.not;
import static robocode.Rules.GUN_TURN_RATE_RADIANS;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.Rules.MAX_VELOCITY;
import static robocode.Rules.MIN_BULLET_POWER;
import static robocode.Rules.RADAR_SCAN_RADIUS;
import static robocode.Rules.getBulletDamage;
import static robocode.Rules.getBulletSpeed;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.AbstractTankDrawingBase.INFO_LEVEL;
import static tankbase.Constant.MAX_NOT_SCAN_TIME;
import static tankbase.Constant.MIN_CHANGE_TARGET_TIME;
import static tankbase.Constant.RADAR_SEARCH_RADIUS;
import static tankbase.FieldMap.computeDangerMap;
import static tankbase.FieldMap.computeSafeDestination;
import static tankbase.FieldMap.getScale;
import static tankbase.FieldMap.initFieldMap;
import static tankbase.FieldMap.setBattleZone;
import static tankbase.TankUtils.computeTurnGun2Target;
import static tankbase.TankUtils.computeTurnGun2TargetNextPos;
import static tankbase.TankUtils.getPointAngle;
import static tankbase.TankUtils.minMax;
import static tankbase.TankUtils.oppositeAngle;
import static tankbase.TankUtils.trigoAngle;
import static tankbase.enemy.EnemyDB.addEnemy;
import static tankbase.enemy.EnemyDB.countFilteredEnemies;
import static tankbase.enemy.EnemyDB.filterAndSortEnemies;
import static tankbase.enemy.EnemyDB.filterEnemies;
import static tankbase.enemy.EnemyDB.getCloseAliveEnemy;
import static tankbase.enemy.EnemyDB.getCloseScannedEnemy;
import static tankbase.enemy.EnemyDB.getEnemy;
import static tankbase.enemy.EnemyDB.listAllEnemies;
import static tankbase.gun.log.FireLog.clearFireLog;
import static tankbase.gun.log.FireLog.getFireByDirection;
import static tankbase.gun.log.FireLog.getFireLog;
import static tankbase.gun.log.FireLog.logFire;
import static tankbase.gun.log.FireLog.removeFire;
import static tankbase.gun.log.VirtualFireLog.clearVirtualFireLog;
import static tankbase.gun.log.VirtualFireLog.logVirtualFire;
import static tankbase.gun.log.VirtualFireLog.updateVirtualFires;
import static tankbase.wave.WaveLog.clearWaveLog;
import static tankbase.wave.WaveLog.getWave;
import static tankbase.wave.WaveLog.getWaves;
import static tankbase.wave.WaveLog.removeWave;
import static tankbase.wave.WaveLog.removeWaves;
import static tankbase.wave.WaveLog.updateWaves;

abstract public class AbstractTankBase extends AbstractCachedTankBase implements ITank {
    private static final List<Gun> guns = new ArrayList<>();
    public static double FIELD_WIDTH;
    public static double FIELD_HEIGHT;
    public static Point2D.Double BATTLE_FIELD_CENTER;
    public static double DISTANCE_MAX;
    public static double GUN_COOLING_RATE;
    public static boolean BIG_BATTLE_FIELD;
    public static PrintStream sysout;


    private static FireStat gpStat;
    private static HeadOnGun headOnGunner = null;

    public int moveLogMaxSize;
    public Enemy target;
    public Point2D.Double destination;
    public Collection<SearchPoint> searchPoints;
    public double scanDirection = 1;
    public double forward = 1;
    public double turnLeft = 0;
    public double turnGunLeft = 0;
    public double turnRadarLeft = 0;
    public double ahead = 0;
    public double firePower = 0;
    protected Enemy mostLeft;
    protected Enemy mostRight;
    protected Aiming aiming = null;
    protected long aliveCount;
    long logDate = 0;
    private final int pathStep = 0;
    private Enemy prevTarget = null;
    private long scanCount;
    private long prevScanCount;
    private boolean alive;
    private boolean running;
    private long lastTargetChange;

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GPBase logic
    private Fire[] targetFireLog;

    protected AbstractTankBase() {
        super();
        setupGuns();
        gpStat = new FireStat();
    }

    private static void putGun(Gun gun) {
        guns.add(gun);
    }

    // GP Robot overide this method
    public void doGP() {
    }

    public void doTurn() {
        //resetChrono();
        doUpdates();
        //getChrono("doUpdate");
        computeDestination();
        //getChrono("computeDestination");
        computeAiming();
        //getChrono("computeAiming");
        selectTarget();
        //getChrono("selectTarget");
        virtualFire();
        //getChrono("virtualFire");
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
        //getChrono("endturn");
    }

    private void doUpdates() {
        //resetChrono();
        updateRobotCache();
        //getChrono("cache");
        updateEnemies();
        //getChrono("enemies");
        updateWaves(getState(), getTime());
        //getChrono("wave");
        updateVirtualFires(getTime());
        //getChrono("virtualFire");
        updateDangerMap();
        //getChrono("danger");
        aliveCount = getAliveCount();
        prevScanCount = scanCount;
        scanCount = getScanCount();
        aiming = null;
        //getChrono("end");

    }

    private void robotSetActions() {
        setTurnRadarLeftRadians(turnRadarLeft);
        setTurnLeftRadians(turnLeft);
        setAhead(ahead);
        setTurnGunLeftRadians(turnGunLeft);
        fireTargetIfPossible();
    }

    private double getTurnRadar() {
        int oc = getOthers();
        if (oc > 0 && (scanCount == oc || (BIG_BATTLE_FIELD && scanCount > 0))) {
            List<Enemy> enemies = filterAndSortEnemies(Enemy::isAlive, (e1, e2) -> {
                double a1 = e1.getAngle();
                double a2 = e2.getAngle();
                return Double.compare(a1, a2);
            });

            if (enemies.isEmpty()) return 2 * PI;

            long lastupdateDelta = updateLeftRightEnemies(enemies);
            if (lastupdateDelta > MAX_NOT_SCAN_TIME && !BIG_BATTLE_FIELD) return 2 * PI;
            double ra = normalAbsoluteAngle(trigoAngle(getRadarHeadingRadians()));
            return scanLeftRight(ra, mostLeft.getAngle(), mostRight.getAngle());
        }
        return 2 * PI;
    }

    private double scanLeftRight(double ra, double ml, double mr) {
        if ((ra >= ml && ra < ml + 2 * Constant.SCAN_OFFSET)
                || (ra < 2 * Constant.SCAN_OFFSET + ml - 2 * PI && ml > 2 * PI - 2 * Constant.SCAN_OFFSET))
            scanDirection = -1;
        else if ((ra < mr && ra > mr - 2 * Constant.SCAN_OFFSET) || (ra > 2 * PI - 2 * Constant.SCAN_OFFSET + mr && mr < 2 * Constant.SCAN_OFFSET))
            scanDirection = 1;

        if (scanDirection == 1)
            return ml + Constant.SCAN_OFFSET - ((ml >= ra) ? ra : ra - 2 * PI);
        else
            return mr - Constant.SCAN_OFFSET - ((mr <= ra) ? ra : ra + 2 * PI);
    }

    private void updateDangerMap() {
        computeDangerMap(filterEnemies(Enemy::isScanned), getEmenmiesMaxDamageMe(), getTime(), getState());
    }

    private long updateLeftRightEnemies(List<Enemy> enemies) {
        Enemy prev = mostRight = enemies.get(0);
        mostLeft = enemies.get(enemies.size() - 1);
        double ba = abs(mostLeft.getAngle() - mostRight.getAngle());
        if (ba > PI) ba = 2 * PI - ba;
        long lastUpdateDelta = 0;
        for (Enemy enemy : enemies) {
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
        if ((getTime() - lastTargetChange) < MIN_CHANGE_TARGET_TIME && target != null && target.isAlive() && target.isScanned())
            return;

        Enemy newTarget = null;
        double minDistance = Double.POSITIVE_INFINITY;

        for (Enemy e : filterEnemies(Enemy::isScanned)) {
            if (e.getFEnergy() < 0 || e.getTurnAimDatas().isEmpty()) continue;

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
            newTarget = getCloseScannedEnemy(getState());

        target = newTarget;

        if (target != null && target.isAlive()) {
            if (target.getState().getEnergy() == 0)
                aiming = headOnGunner.aim(target);
            else
                aiming = target.getBestAiming();
        }

        if (target != prevTarget)
            lastTargetChange = getTime();
    }

    private void computeAiming() {
        filterEnemies(Enemy::isScanned).forEach(this::computeAimingTarget);
    }

    private void computeAimingTarget(Enemy target) {
        ArrayList<Aiming> aimings = new ArrayList<>();
        for (Gun gun : guns) {
            Aiming ad = gun.aim(target);
            if (ad != null) {
                aimings.add(ad);
            }
        }
        target.setTurnAimDatas(aimings);
    }

    private void updateEnemies() {
        filterEnemies(Enemy::isAlive).forEach(Enemy::move);
    }

    public double getEmenmiesMaxDamageMe() {
        return listAllEnemies().stream().filter(Enemy::isAlive).map(Enemy::getDamageMe)
                .max(Double::compare).orElse(0.0);
    }

    private void computeDestination() {
        if (BIG_BATTLE_FIELD)
            computeBigBattleFieldDestination();
        else
            destination = computeSafeDestination(getState());
    }

    private void computeBigBattleFieldDestination() {
        if (INFO_LEVEL > 2)
            out.printf("computeBigBattleFieldDestination scanCount=%d%n", scanCount);

        if (scanCount > 0)
            destinationWithScans();
        else
            searchPathDestination();
    }

    private Point2D.Double getClosestSearchPath(Point2D.Double p) {
        return searchPoints.stream()
                .min(Comparator.comparingInt(SearchPoint::visited).thenComparingDouble(p::distance))
                .orElse(null);
    }

    void printEnemyStatus() {
        listAllEnemies().forEach(e -> {
            sysout.printf("%s isAlive=%b isScan=%b%n", e.getName(), e.isAlive(), e.isScanned());
        });
    }

    private void destinationWithScans() {
        double scale = getScale();
        double r = RADAR_SCAN_RADIUS / scale;

        if (INFO_LEVEL > 2)
            printEnemyStatus();

        if (prevScanCount == 0) {
            // We create a circle battle zone around closest enemy
            TankState e = getCloseScannedEnemy(getState()).getState();
            Point c = new Point((int) (e.x / scale), (int) (e.y / scale));
            setBattleZone(c, r);
        } else if (target != null && target.isAlive() && target.isScanned()) {
            // We maintain the actual target centered in battle zone so can can continue to scan it
            TankState e = target.getState();
            if (e.distance(getState()) > RADAR_SEARCH_RADIUS) {
                Point c = new Point((int) (e.x / scale), (int) (e.y / scale));
                setBattleZone(c, r);
            }
        }

        destination = computeSafeDestination(getState());
    }

    private void searchPathDestination() {
        // search Enenmy
        if (destination == null) {
            // Get closet alive
            Enemy e = getCloseAliveEnemy(getState());

            if (e != null && getTime() > 0) {
                destination = e.getState();
                if (INFO_LEVEL > 0)
                    sysout.printf("searching for %s at x=%.0f y=%.0f%n",
                            e.getName(), destination.getX(), destination.getY());
            } else
                destination = getClosestSearchPath(getState());
        } else {
            if (destination.distance(getState()) <= MAX_VELOCITY) {
                if (destination instanceof SearchPoint)
                    ((SearchPoint) destination).visit();
            }

            destination = getClosestSearchPath(getState());
        }
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
            if (INFO_LEVEL > 1)
                out.printf("Fire on %s rejected by tolerance, turn remaining=%.02f,  offset=%.02f\n", target.getName(), getTurnGun(), getGunHeadingRadians() - a);
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
        }
    }

    private void virtualFire() {
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

        // if no solution, just turn to target
        if (aiming == null) {
            TankState targetState = target.getState().extrapolateNextState();
            if (targetState != null)
                return computeTurnGun2TargetNextPos(this, targetState);
            return computeTurnGun2TargetNextPos(this, target.getState());
        }

        return computeTurnGun2Target(getNextPosition(), aiming.getFiringPosition(), getGunHeadingRadians());
    }

    public long getAliveCount() {
        return countFilteredEnemies(Enemy::isAlive);
    }

    public long getDeadCount() {
        return countFilteredEnemies(not(Enemy::isAlive));
    }

    public long getScanCount() {
        return countFilteredEnemies(Enemy::isScanned);
    }

    public long unScanCount() {
        return countFilteredEnemies(not(Enemy::isScanned));
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
        BIG_BATTLE_FIELD = FIELD_HEIGHT > RADAR_SCAN_RADIUS;

        initFieldMap();
        moveLogMaxSize = (int) (DISTANCE_MAX / getBulletSpeed(MAX_BULLET_POWER) + 2);
        if (BIG_BATTLE_FIELD)
            searchPoints = computeSearchPath();
        updateRobotCache();
        aliveCount = super.getOthers();
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setColors(Color.red, Color.blue, Color.green);

        running = alive = true;
        resetRoundData();

        while (running) {
            doTurn();
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent sre) {
        onEvent(sre);
        String name = sre.getName();
        Enemy enemy = getEnemy(name);
        EnemyDetectedEvent ede = new EnemyDetectedEvent(sre);

        if (enemy == null)
            addEnemy(new Enemy(ede, name, this));
        else
            enemy.update(ede, this);
    }

    @Override
    public void onBulletHit(BulletHitEvent bhe) {
        onEvent(bhe);
        gpStat.hit(bhe.getEnergy());
        Optional<Fire> of = getFireByDirection(trigoAngle(bhe.getBullet().getHeadingRadians()));
        of.ifPresent(f -> {
            removeFire(f);
            String name = bhe.getName();
            Enemy enemy = getEnemy(bhe.getName());
            if (enemy != null) {
                enemy.getState().setEnergy(bhe.getEnergy());
            }

            if (BIG_BATTLE_FIELD && getState().distance(f.getPosition(getTime())) > RADAR_SCAN_RADIUS) {
                if (enemy == null) {
                    enemy = new Enemy(new EnemyDetectedEvent(bhe, f), name, this);
                    addEnemy(enemy);
                } else
                    enemy.update(new EnemyDetectedEvent(bhe, f, enemy.getState()), this);

                if (INFO_LEVEL > 0)
                    sysout.printf("Beyond Radar Range detection of %s at x=%.0f, y=%.0f%n",
                            name, enemy.getState().getX(), enemy.getState().getY());
            }
        });
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
        ow.ifPresent(wave -> {
            removeWave(wave);
            wave.getSource().addFEnergy(getBulletDamage(b.getPower()));

        });

        Optional<Fire> of = getFireByDirection(trigoAngle(bhbe.getBullet().getHeadingRadians()));
        of.ifPresent(fire -> {
            removeFire(fire);
        });
    }

    @Override
    public void onHitByBullet(HitByBulletEvent hbbe) {
        onEvent(hbbe);
        Enemy e = getEnemy(hbbe.getName());
        if (e == null) return;

        e.damageMe(getBulletDamage(hbbe.getPower()));

        Bullet b = hbbe.getBullet();
        Point2D.Double p = new Point2D.Double(b.getX(), b.getY());
        Optional<Wave> ow = getWave(hbbe.getName(), p, getTime());
        List<Wave> toRemove = new ArrayList<>();
        List<Wave> toLog = new ArrayList<>();
        ow.ifPresent(wave -> {
            double bulletHeading = trigoAngle(hbbe.getHeadingRadians());
            double headOn = getPointAngle(wave.getWaveStart(), wave.getHead());
            double circular = getPointAngle(wave.getWaveStart(), wave.getCircular());
            double ratio = minMax((bulletHeading - headOn) / (circular - headOn), -1, 1);
            //sysout.printf("%s change angle ratio from %02f to %02f%n", e.getName(), e.getFireAngleRatio(), ratio);
            e.setFireAngleRatio(ratio);
            removeWave(wave);
            getWaves().stream().filter(w -> w.getSource() == wave.getSource() && w != wave)
                    .forEach(w -> {
                        toLog.add(w.updateWithRatio(ratio));
                        toRemove.add(w);
                    });
        });
        removeWaves(toRemove);
        toLog.forEach(WaveLog::logWave);
    }

    @Override
    public void onDeath(DeathEvent event) {
        onEvent(event);
        alive = false;
    }

    @Override
    public void onRoundEnded(RoundEndedEvent event) {
        onEvent(event);
        printStat();
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        onEvent(event);
        String name = event.getName();
        Enemy enemy = getEnemy(name);
        if (enemy != null) {
            enemy.die();
            if (enemy == target) {
                target = null;
                if (BIG_BATTLE_FIELD)
                    destination = null;
            }
        }
    }

    private void onEvent(Event e) {
        updateRobotCache(e.getTime());
    }

    @Override
    public void onSkippedTurn(SkippedTurnEvent event) {
        out.printf("Skip turn: %d %d%n", event.getSkippedTurn(), event.getPriority());
    }

    @Override
    public void onHitRobot(HitRobotEvent hre) {
        onEvent(hre);
        Enemy e = getEnemy(hre.getName());
        if (e != null) {
            target = e;
            lastTargetChange = hre.getTime();
        }
    }

    // ///////////////////////////////////////////////////////////////////////////////////////
    // target stuff
    public int countTargetFire() {
        if (target == null) return 0;
        checkFireLog();
        return targetFireLog.length;
    }

    public double bulletPower(int i) {
        if (countTargetFire() > i)
            return targetFireLog[i].getAimingData().getFirePower();
        return 0;
    }

    public double bulletSpeed(int i) {
        if (countTargetFire() > i)
            return targetFireLog[i].getVelocity();
        return 0;
    }

    public Point2D.Double bulletPos(int i) {
        if (countTargetFire() > i)
            return targetFireLog[i].getPosition(getTime());
        return getState();
    }

    public double bulletDistance(int i) {
        if (countTargetFire() > i)
            return targetFireLog[i].getPosition(getTime()).distance(target.getState());
        return 0;
    }

    public double bulletHeading(int i) {
        if (countTargetFire() > i)
            return targetFireLog[i].getDirection();
        return 0;
    }

    public double bulletRelativeAngle(int i) {
        if (countTargetFire() > i) {
            double a = getPointAngle(targetFireLog[i].getPosition(getTime()), target.getState());
            return normalRelativeAngle(target.getState().getHeadingRadians() - a);
        }
        return 0;
    }

    private void checkFireLog() {
        if (getTime() != logDate && target != null) {
            targetFireLog = getFireLog(target.getName()).toArray(new Fire[0]);
        }
    }

    // ///////////////////////////////////////////////////////////////////////////////////////
    // Private stuff
    private Collection<SearchPoint> computeSearchPath() {
        double dx = FIELD_WIDTH / (1 + (int) (FIELD_WIDTH / RADAR_SEARCH_RADIUS));
        double dy = FIELD_HEIGHT / (1 + (int) (FIELD_HEIGHT / RADAR_SEARCH_RADIUS));

        List<SearchPoint> sp = new ArrayList<>();
        for (double y = 1; y * dy < FIELD_HEIGHT; y++)
            for (double x = 1; x * dx < FIELD_WIDTH; x++)
                sp.add(new SearchPoint(x * dx, y * dy));

        return sp;
    }

    private void resetRoundData() {
        clearFireLog();
        clearVirtualFireLog();
        clearWaveLog();
        mostLeft = mostRight = null;
        target = prevTarget = null;
        lastTargetChange = 0;
        alive = true;
        listAllEnemies().forEach(Enemy::reset);
        if (BIG_BATTLE_FIELD)
            searchPoints.forEach(SearchPoint::reset);
        updateGuns();
        aliveCount = getOthers();
        scanCount = 0;
    }

    private void resetRoundStat() {
        guns.forEach(gun -> gun.resetRoundStat());
    }

    private void printStat() {
        guns.forEach(gun -> {
            out.printf("==== %s ====%n", gun.getName());
            listAllEnemies().forEach(enemy -> {
                FireStat fs = gun.getEnemyRoundFireStat(enemy);
                out.printf("    %s hitrate = %.0f%% / %d, dmg/cost=%.0f%%%n", enemy.getName(), fs.getHitRate() * 100, fs.getFireCount(),
                        fs.getDommageCostRatio() * 100);
            });
        });

        /*listAllEnemies().forEach(enemy -> {
            double hitRate = 0;
            int fireCount = 0;
            double damageCostRatio = 0;
            for (Gun gun : guns) {
                FireStat fs = gun.getEnemyRoundFireStat(enemy);
                hitRate += fs.getHitRate();
                fireCount += fs.getFireCount();
                damageCostRatio += fs.getDommageCostRatio();
            }
            hitRate /= guns.size();
            damageCostRatio /= guns.size();
            out.printf("==== %s hitrate = %.0f%%(%d) / %d, dmg/cost=%.0f%%%n", enemy.getName(), hitRate * 100, (int) (hitRate * fireCount), fireCount, damageCostRatio * 100);
            out.printf("==== %s hitMe = %d, dmgt=%.0f%%%n", enemy.getName(), enemy.getHitMe(), enemy.getDamageMe());
        });*/
        resetRoundStat();
    }

    public Point2D.Double getNextPosition() {
        TankState next = getState().extrapolateNextState();
        if (next != null)
            return next;
        return getState();
    }

    private void setupGuns() {
        if (guns.size() == 0) {
            headOnGunner = new HeadOnGun(this);
            //putGun(headOnGunner);
            putGun(new CircularGun(this));
            putGun(new ClusterGun(this));
            putGun(new AntiSurferGun(this));
        }
    }

    private void updateGuns() {
        headOnGunner.setFirer(this);
        guns.forEach(gun -> gun.setFirer(this));
    }
}