package tankbase;

import robocode.*;
import robocode.Event;
import tankbase.enemy.Enemy;
import tankbase.enemy.EnenyDetectedEvent;
import tankbase.gun.*;
import tankbase.gun.log.FireLog;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintStream;
import java.util.*;
import java.util.List;

import static java.lang.Math.*;
import static java.util.function.Predicate.not;
import static robocode.Rules.*;
import static robocode.util.Utils.*;
import static tankbase.Constant.*;
import static tankbase.FieldMap.*;
import static tankbase.TankUtils.*;
import static tankbase.WaveLog.*;
import static tankbase.enemy.EnemyDB.*;
import static tankbase.gun.log.FireLog.*;
import static tankbase.gun.log.VirtualFireLog.*;

abstract public class AbstractTankBase extends AbstractCachedTankBase implements ITank {
    private static final Map<String, Gunner> gunners = new HashMap<>();
    public static double FIELD_WIDTH;
    public static double FIELD_HEIGHT;
    public static Point2D.Double BATTLE_FIELD_CENTER;
    public static double DISTANCE_MAX;
    public static double GUN_COOLING_RATE;
    public static boolean BIG_BATTLE_FIELD;
    public static PrintStream sysout;


    private static FireStat gpStat;
    private static HeadOnGunner headOnGunner = null;

    public int moveLogMaxSize;
    public Enemy target;
    public Point2D.Double destination;
    public Point2D.Double[] searchPath;
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
        if ((scanCount==oc || (BIG_BATTLE_FIELD && scanCount > 0)) && oc > 0) {
            long lastupdateDelta = updateLeftRightEnemies();
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
        computeDangerMap(listEnemies(), getEmenmiesMaxDamageMe(), getTime(), getState());
    }

    private long updateLeftRightEnemies() {
        List<Enemy> sEnemies = filterAndSortEnemies(Enemy::isAlive, (e1, e2) -> {
            double a1 = e1.getAngle();
            double a2 = e2.getAngle();
            return Double.compare(a1, a2);
        });

        Enemy prev = mostRight = sEnemies.getFirst();
        mostLeft = sEnemies.getLast();
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
        if ((getTime() - lastTargetChange) < MIN_CHANGE_TARGET_TIME && target != null && target.isAlive() && target.isScanned())
            return;

        Enemy newTarget = null;
        double minDistance = Double.POSITIVE_INFINITY;

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

    private void computeDestination() {
        if (BIG_BATTLE_FIELD)
            computeBibBattleFieldDestination();
        else
            destination = computeSafeDestination(getState(), listEnemies());
    }

    private void computeBibBattleFieldDestination() {
        if (scanCount > 0) {
            destinationWithScans();
        } else {
            searchPathDestination();
        }
    }

    private int nextSearchStep() {
        return (pathStep+1)%searchPath.length;
    }

    private int prevSearchStep() {
        return pathStep == 0 ? searchPath.length-1 : pathStep -1;
    }

    private int getClosestSearchPath(Point2D.Double p) {
        double dmin = Double.MAX_VALUE;
        for (int i=0; i<searchPath.length; i++) {
            double d = searchPath[i].distance(getState());
            if (d<dmin) {
                dmin = d;
                pathStep = i;
            }
        }
        return pathStep;
    }

    private void destinationWithScans() {
        double scale = getScale();
        double r = RADAR_SCAN_RADIUS/scale;

        if (prevScanCount == 0) {
            // We create a circle battle zone around closest enemy
            TankState e = getCloseAliveEnemy(getState()).getState();
            Point c = new Point((int) (e.x / scale), (int) (e.y / scale));
            setBattleZone(c, r);
        } else
            if (target != null) {
                // We maintain the actual target centered in battle zone so can can continue to scan it
                TankState e = target.getState();
                if (e.distance(getState()) > RADAR_SEARCH_RADIUS) {
                    Point c = new Point((int) (e.x / scale), (int) (e.y / scale));
                    setBattleZone(c, r);
                }
            }

        destination = computeSafeDestination(getState(), listEnemies());
    }

    private void searchPathDestination() {
        // search Enenmy

        // Get closet
        Enemy e = getCloseAliveEnemy(getState());

        if (e != null)
            destination = e.getState();
        else {
            if (destination == null)
                pathStep = getClosestSearchPath(getState());
            else {
                if (destination.distance(getState()) <= TANK_SIZE)
                    pathStep = nextSearchStep();
            }

            destination = searchPath[pathStep];
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
        return listEnemies().stream().filter(Enemy::isAlive).count();
    }

    public long getScanCount() {
        return listEnemies().stream().filter(Enemy::isScanned).count();
    }

    public long unScanCount() {
        return listEnemies().stream().filter(not(Enemy::isScanned)).count();
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
            searchPath = computeSearchPath();
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
        of.ifPresent(f -> {
            removeFire(f);
            String name = bhe.getName();
            Enemy enemy = getEnemy(bhe.getName());

           /* if (enemy == null) {
                enemy=new Enemy(new EnenyDetectedEvent(bhe, f), name, this);
                sysout.printf("BVR detection of %s at x=%.0f, y%.0f%n",
                        name, enemy.getState().getX(), enemy.getState().getY());
                addEnemy(enemy);
            } else
                enemy.update(new EnenyDetectedEvent(bhe, f),this);
            */
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
        Enemy enemy = getEnemy(event.getName());
        if (enemy != null) // If robot die before we scan it
            enemy.die();
        scanCount--;
        aliveCount--;
        if (enemy==target)
            target=null;

        if (BIG_BATTLE_FIELD && scanCount == 0)
            destination = null; // To force get close search path

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
    private Point2D.Double[] computeSearchPath() {
        double dx= FIELD_WIDTH / (1+(int) (FIELD_WIDTH/RADAR_SEARCH_RADIUS));
        double dy=FIELD_HEIGHT / (1+(int) (FIELD_HEIGHT/RADAR_SEARCH_RADIUS));

        double offsetX = dx;
        double offsetY = dy;

        Double width = FIELD_WIDTH - dx*2;
        Double height = FIELD_HEIGHT - dy*2;

        LinkedList<Point2D> pList = new LinkedList<>();

        while(height > 0) {
            int i = (int) (width / dx)+1;
            int j = (int) (height / dy)+1;

            for (int x=0 ; x<=i ; x++) pList.add(new Point2D.Double(offsetX + x*dx, offsetY));
            for (int y=1 ; y<=j ; y++) pList.add(new Point2D.Double(FIELD_WIDTH-offsetX, offsetY + y*dy));
            for (int x=i-1 ; x>=0 ; x--) pList.add(new Point2D.Double(offsetX + x*dx, FIELD_HEIGHT-offsetY));
            for (int y=j-1 ; y>0 ; y--) pList.add(new Point2D.Double(offsetX, offsetY + y*dy));

            offsetX +=dx;
            offsetY +=dy;
            width -= dx*2;
            height -= dy*2;
        }

        int i = (int) (width / RADAR_SEARCH_RADIUS) + 1;
        for (int x=0 ; x<i ; x++) pList.add(new Point2D.Double(offsetX + x*dx, offsetY));

        return pList.toArray(new Point2D.Double[0]);
    }

    private void resetRoundData() {
        clearFireLog();
        clearVirtualFireLog();
        clearWaveLog();
        mostLeft = mostRight = null;
        target = prevTarget = null;
        lastTargetChange = 0;
        alive = true;
        listEnemies().forEach(Enemy::reset);
        updateGunners();
        aliveCount = getOthers();
        scanCount = 0;
    }

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