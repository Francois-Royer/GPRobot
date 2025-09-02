package tankbase;

import robocode.*;
import robocode.Event;
import tankbase.enemy.Enemy;
import tankbase.enemy.EnemyDetectedEvent;
import tankbase.gun.*;
import tankbase.wave.Wave;
import tankbase.wave.WaveLog;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintStream;
import java.util.*;
import java.util.List;

import static java.lang.Math.*;
import static java.util.function.Predicate.not;
import static robocode.Rules.*;
import static robocode.util.Utils.*;
import static tankbase.AbstractTankDrawingBase.INFO_LEVEL;
import static tankbase.Constant.*;
import static tankbase.FieldMap.*;
import static tankbase.TankUtils.*;
import static tankbase.wave.WaveLog.*;
import static tankbase.enemy.EnemyDB.*;
import static tankbase.gun.log.FireLog.*;
import static tankbase.gun.log.VirtualFireLog.*;

abstract public class AbstractTankBase extends AbstractCachedTankBase implements ITank {
    private static final Map<String, Gun> guns = new HashMap<>();
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
    private int pathStep = 0;

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
    private Enemy prevTarget = null;

    private long aliveCount;
    private long scanCount;
    private long prevScanCount;

    private boolean alive;
    private boolean running;
    private long lastTargetChange;

    protected AbstractTankBase() {
        super();
        setupGuns();
        gpStat = new FireStat();
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GPBase logic

    // GP Robot overide this method
    public void doGP() {
    }

    public void doTurn() {
        doUpdates();
        computeDestination();
        computeAiming();
        selectTarget();
        virtualFire();
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

    private void doUpdates() {
        updateRobotCache();
        updateEnemies();
        updateWaves(getState(), getTime());
        updateVirtualFires(getTime());
        updateDangerMap();
        aliveCount = getAliveCount();
        prevScanCount = scanCount;
        scanCount = getScanCount();
        aiming = null;
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
        if (oc > 0 && (scanCount==oc || (BIG_BATTLE_FIELD && scanCount > 0))) {
            List<Enemy> enemies = filterAndSortEnemies(Enemy::isAlive, (e1, e2) -> {
                double a1 = e1.getAngle();
                double a2 = e2.getAngle();
                return Double.compare(a1, a2);
            });

            if (enemies.isEmpty()) return 2*PI;

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
        computeDangerMap(filterEnemies(Enemy::isAlive), getEmenmiesMaxDamageMe(), getTime(), getState());
    }

    private long updateLeftRightEnemies(List<Enemy> enemies) {
        Enemy prev = mostRight = enemies.getFirst();
        mostLeft = enemies.getLast();
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

        for (Enemy e : listAllEnemies()) {
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
        filterEnemies(Enemy::isAlive).forEach(this::computeAimingTarget);
    }

    private void computeAimingTarget(Enemy target) {
        ArrayList<Aiming> aimings = new ArrayList<>();
        for (Gun gun : guns.values()) {
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
        if (INFO_LEVEL>2)
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
        listAllEnemies().forEach(e-> {
            sysout.printf("%s isAlive=%b isScan=%b%n", e.getName(), e.isAlive(), e.isScanned());
        });
    }

    private void destinationWithScans() {
        double scale = getScale();
        double r = RADAR_SCAN_RADIUS/scale;

        if (INFO_LEVEL>2)
            printEnemyStatus();

        if (prevScanCount == 0) {
            // We create a circle battle zone around closest enemy
            TankState e = getCloseScannedEnemy(getState()).getState();
            Point c = new Point((int) (e.x / scale), (int) (e.y / scale));
            setBattleZone(c, r);
        } else
            if (target != null && target.isAlive() && target.isScanned()) {
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

            if (e != null && getTime()>0) {
                destination = e.getState();
                if (INFO_LEVEL>0)
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
                out.printf("Fire on %s rejected by tolerance, turn remaining=%.02f,  offset=%.02f\n", target.getName(), getTurnGun(), getGunHeadingRadians()-a);
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
        //if (getGunHeat() > 0) return;

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
        moveLogMaxSize = (int) (DISTANCE_MAX / getBulletSpeed(MAX_BULLET_POWER) + 1);
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
            if (BIG_BATTLE_FIELD && getState().distance(f.getPosition(getTime())) > RADAR_SCAN_RADIUS) {
                String name = bhe.getName();
                Enemy enemy = getEnemy(bhe.getName());

                if (enemy == null) {
                    enemy = new Enemy(new EnemyDetectedEvent(bhe, f), name, this);
                    addEnemy(enemy);
                } else
                    enemy.update(new EnemyDetectedEvent(bhe, f, enemy.getState()), this);

                if (INFO_LEVEL>0)
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

        Bullet b = hbbe.getBullet();
        Point2D.Double p = new Point2D.Double(b.getX(), b.getY());
        Optional<Wave> ow = getWave(hbbe.getName(), p, getTime());
        ow.ifPresent(wave -> {
            double bulletHeading = trigoAngle(hbbe.getHeadingRadians());
            double headOn = getPointAngle(wave, wave.getHead());
            double circular = getPointAngle(wave, wave.getCircular());

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
        running = alive = false;
    }

    @Override
    public void onRoundEnded(RoundEndedEvent event) {
        onEvent(event);
        running = false;
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

    /// ///////////////////////////////////////////////////////////////////////////////////////
    // Private stuff
    private Collection<SearchPoint> computeSearchPath() {
        double dx= FIELD_WIDTH / (1+(int) (FIELD_WIDTH/RADAR_SEARCH_RADIUS));
        double dy=FIELD_HEIGHT / (1+(int) (FIELD_HEIGHT/RADAR_SEARCH_RADIUS));

        List<SearchPoint> sp = new ArrayList<>();
        for (double y=1; y*dy < FIELD_HEIGHT; y++)
            for (double x=1; x*dx < FIELD_WIDTH; x++)
                sp.add(new SearchPoint(x*dx, y*dy));

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
        guns.values().forEach(gun -> gun.resetRoundStat());
    }

    private void printStat() {
        guns.values().forEach(gun -> {
            out.printf("==== %s ====%n", gun.getName());
            listAllEnemies().forEach(enemy -> {
                FireStat fs = gun.getEnemyRoundFireStat(enemy);
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

    private static void putGun(Gun gun) {
        guns.put(gun.getName(), gun);
    }

    private void setupGuns() {
        if (guns.size() == 0) {
            headOnGunner = new HeadOnGun(this);
            putGun(new CircularGun(this));
            putGun(new ClusterGun(this));
            putGun(new AntiSurferGun(this));
        }
    }

    private void updateGuns() {
        headOnGunner.setFirer(this);
        guns.values().forEach(gun -> gun.setFirer(this));
    }
}