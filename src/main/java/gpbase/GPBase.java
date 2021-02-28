package gpbase;

import static gpbase.GPUtils.*;

import gpbase.gun.*;
import robocode.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static gprobot.RobocodeConf.ROUNDS;
import static java.lang.Math.*;
import static robocode.util.Utils.*;
import static robocode.Rules.*;

public class GPBase extends AdvancedRobot {
    Point.Double BATTLE_FIELD_CENTER;
    public static double TANK_SIZE = 36;

    public double BORDER_OFFSET = TANK_SIZE * 3 / 2;
    public double SCAN_OFFSET = RADAR_TURN_RATE_RADIANS / 3;
    public static double FIRE_TOLERANCE = TANK_SIZE / 4;
    public static long FIRE_AGAIN_MIN_TIME;

    public static double dmin = TANK_SIZE * 2;
    public static double dmax;
    public static Random random = getRandom();

    public int aimingMoveLogSize;
    public int moveLogMaxSize;

    public int aliveCount;
    public int enemyCount;

    public Point.Double unsafePosition;
    public Point.Double safePosition;
    public Point.Double waveSafePosition;

    public double scandirection = 1;
    public double forward = 1;

    public double turnLeft = 0;
    public double turnGunLeft = 0;
    public double turnRadarLeft = 0;
    public double ahead = 0;
    public double fire = 0;

    static Map<String, Gunner> gunners = new HashMap<>();
    static List<AimingData> aimDatas = new ArrayList<>();
    static Map<String, Enemy> enemies = new HashMap<>();
    static List<Wave> waves = new ArrayList<>();
    static List<VShell> vShells = new ArrayList<>();

    public Enemy target;
    public Enemy mostLeft;
    public Enemy mostRight;
    public AimingData aimingData = null;

    public long lastFireTime = -1;
    public long now;

    public GPBase() {
        super();
        setupGunners();
    }

    @Override
    public void run() {
        BATTLE_FIELD_CENTER = new Point.Double(getBattleFieldWidth() / 2, getBattleFieldHeight() / 2);
        dmax = BATTLE_FIELD_CENTER.distance(TANK_SIZE / 2, TANK_SIZE / 2) * 2;
        aimingMoveLogSize = (int) (dmax / getBulletSpeed(MAX_BULLET_POWER) + 2);
        moveLogMaxSize = aimingMoveLogSize * 100;
        FIRE_AGAIN_MIN_TIME = (long) (Rules.getGunHeat(MIN_BULLET_POWER) / getGunCoolingRate());
        //out.println("dmax=" + dmax);
        //out.println("aimingMoveLogSize=" + aimingMoveLogSize);
        //out.println("FIRE_AGAIN_MIN_TIME=" + FIRE_AGAIN_MIN_TIME);

        enemyCount = aliveCount = getOthers();

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setColors(Color.red, Color.blue, Color.green);

        for (Enemy enemy : enemies.values()) {
            enemy.setEnergy(0);
            enemy.rotationRate = 0;
            enemy.velocity = enemy.scanVelocity = 0;
            enemy.alive = false;
            enemy.scanLastUpdate = 0;
            enemy.lastFire = 0;
            enemy.fEnergy = 0;
        }

        while (true) {
            doTurn();
            execute();
        }

    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        Enemy enemy = enemies.get(e.getName());

        if (enemy == null)
            enemies.put(e.getName(), new Enemy(e, this));
        else
            enemy.update(e);
    }

    @Override
    public void onBulletHit(BulletHitEvent bhe) {
        Enemy e = enemies.get(bhe.getName());
        if (e != null) {
            e.setEnergy(bhe.getEnergy(), getBulletDamage(bhe.getBullet().getPower()));

            AimingData ad = getAimingDataByAngle(bhe.getBullet().getHeadingRadians());
            if (ad != null) {
                if (e.getName() == ad.getTarget().getName()) {
                    ad.getGunner().hit(ad.getTarget());
                    e.hit(ad.getKdPoint());
                }
                ///else
                //out.printf("%s hit %s but was aiming to %s...\n",
                //ad.getGunner().getName(), e.getName(), ad.getTarget().getName());
                ad.getTarget().fEnergy += getBulletDamage(ad.getFirePower());
                aimDatas.remove(ad);
            }
        }
    }

    @Override
    public void onBulletMissed(BulletMissedEvent bme) {
        AimingData ad = getAimingDataByAngle(bme.getBullet().getHeadingRadians());
        if (ad != null) {
            //out.printf("miss %s\n", ad.getTarget().name);
            aimDatas.remove(ad);
            ad.getTarget().miss(ad.getKdPoint());
            ad.getTarget().fEnergy += getBulletDamage(ad.getFirePower());
            //out.printf("restored energy %f\n", ad.getTarget().fEnergy);
        }
    }


    @Override
    public void onBulletHitBullet(BulletHitBulletEvent bhbe) {
        now = bhbe.getTime();
        if (waves.size() > 0) {
            Bullet b = bhbe.getHitBullet();
            Point.Double p = new Point.Double(b.getX(), b.getY());
            Optional<Wave> ow = waves.stream().filter(w -> w.name == b.getName())
                .sorted(new WaveComparator(p, now)).findFirst();

            if (ow.isPresent())
                waves.remove(ow.get());
        }

        AimingData ad = getAimingDataByAngle(bhbe.getBullet().getHeadingRadians());
        if (ad != null) {
            //out.printf("%s fire %s but bullet hit by bullet...\n",
            //ad.getGunner().getName(), ad.getTarget().getName());
            aimDatas.remove(ad);
            ad.getTarget().fEnergy += getBulletDamage(ad.getFirePower());
        }
    }

    @Override
    public void onHitByBullet(HitByBulletEvent hbbe) {
        now = hbbe.getTime();
        Enemy e = enemies.get(hbbe.getName());
        if (e != null)
            e.setEnergy(e.getEnergy() + getBulletHitBonus(hbbe.getPower()));

        if (waves.size() > 0) {
            Optional<Wave> ow = waves.stream().filter(w -> w.name == hbbe.getName())
                .sorted(new WaveComparator(getCurrentPoint(), now)).findFirst();

            if (ow.isPresent())
                waves.remove(ow.get());
        }
    }

    @Override
    public void onRoundEnded(RoundEndedEvent event) {
        resetRoundStat();
    }

    @Override
    public void onDeath(DeathEvent event) {
        resetRoundStat();
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        Enemy enemy = enemies.get(event.getName());
        enemy.setEnergy(0);
        enemy.fEnergy = 0;
        enemy.rotationRate = 0;
        enemy.velocity = enemy.scanVelocity = 0;
        enemy.alive = false;
        enemy.scanLastUpdate = 0;
        enemy.lastFire = 0;
        aliveCount--;
    }

    @Override
    public void onPaint(Graphics2D g) {
        if (safePosition != null) {
            drawFillCircle(g, Color.GREEN, safePosition, 10);
            drawFillCircle(g, Color.RED, unsafePosition, 10);
        }

        for (Enemy e : enemies.values())
            if (e.alive)
                drawCircle(g, Color.PINK, e, (int) TANK_SIZE);

        if (aimingData != null) {
            for (Point.Double p : aimingData.getExpectedMoves())
                drawFillCircle(g, Color.yellow, p, 5);

            drawAimCircle(g, Color.CYAN, aimingData.getFiringPosition(), 20);
        }

        if (mostLeft != null && mostRight != null) {
            drawCircle(g, Color.RED, mostLeft, (int) TANK_SIZE * 4 / 3);
            drawCircle(g, Color.GREEN, mostRight, (int) TANK_SIZE * 4 / 3);
        }

        now = getTime();
        for (Wave w : waves)
            drawWave(g, Color.ORANGE, w, now);
        if (waves.size() > 0) {
            Wave w = waves.stream().sorted(new WaveComparator(safePosition, now)).findFirst().get();
            drawWave(g, Color.RED, w, now);
        }

        for (VShell vs : vShells) {
            drawFillCircle(g, Color.MAGENTA, vs.getPosition(now), 5);
        }
    }

    @Override
    public void onSkippedTurn(SkippedTurnEvent event) {
        out.printf("Skip turn: %d %d\n", event.getSkippedTurn(), event.getPriority());
    }
    // public void onHitWall(HitWallEvent e) { forward *= -1; }
    //

    @Override
    public void onHitRobot(HitRobotEvent e) {
        // Try escape backward and turn PI/2
        forward *= -1;
        setTurnLeftRadians(PI / 2);
        setAhead(forward * TANK_SIZE * 2);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GPBase logic
    public void doGP() {

    }

    public void doTurn() {
        now = getTime();
        updatePositions();
        updateWaves();
        updateVShells();
        computeSafePosition();

        int oc = getOthers();
        if (oc > 0 && aliveCount() >= oc) {
            updateLeftRightEnemies();
            double ra = normalAbsoluteAngle(trigoAngle(getRadarHeadingRadians()));
            turnRadarLeft = scanLeftRight(ra, mostLeft.angle, mostRight.angle);
        } else
            turnRadarLeft = 2 * PI;

        turnLeft = getTurn2Safe();
        ahead = getSafeAhead();
        turnGunLeft = getTurn2Targert();
        fire = (aimingData == null) ? 0 : aimingData.getFirePower();
        if (aimingData != null) {
            //out.printf("before GP: %.02f %.02f %.02f %.02f: %s %.02f\n", turnLeft, ahead, turnGunLeft, fire, aimingData.getGunner().getName(), aimingData.getGunner().hitRate(aimingData.getTarget()) * 100);
            doGP();
            //out.printf("after GP: %.02f %.02f %.02f %.02f\n", turnLeft, ahead, turnGunLeft, fire);
        }
        robotSetActions();
    }

    class WaveComparator implements Comparator<Wave> {
        Point.Double p;
        long tick;

        public WaveComparator(Point.Double p, long tick) {
            this.p = p;
            this.tick = tick;
        }

        @Override
        public int compare(Wave w1, Wave w2) {
            return (int) (p.distance(w1.getPosition(tick)) - p.distance(w2.getPosition(tick)));
        }
    }

    private void robotSetActions() {
        setAhead(ahead);
        setTurnRadarLeftRadians(turnRadarLeft);
        setTurnLeftRadians(turnLeft);
        setTurnGunLeftRadians(turnGunLeft);
        fireTargetIfPossible(fire);
    }

    private double scanLeftRight(double ra, double ml, double mr) {
        if ((ra >= ml && ra < ml + 2 * SCAN_OFFSET)
            || (ra < 2 * SCAN_OFFSET + ml - 2 * PI && ml > 2 * PI - 2 * SCAN_OFFSET))
            scandirection = -1;
        else if ((ra < mr && ra > mr - 2 * SCAN_OFFSET) || (ra > 2 * PI - 2 * SCAN_OFFSET + mr && mr < 2 * SCAN_OFFSET))
            scandirection = 1;

        if (scandirection == 1)
            return ml + SCAN_OFFSET - ((ml >= ra) ? ra : ra - 2 * PI);
        else
            return mr - SCAN_OFFSET - ((mr <= ra) ? ra : ra + 2 * PI);
    }

    private void updateVShells() {
        List<VShell> newVShells = vShells.stream().filter(vs -> {
            Point.Double p = vs.getPosition(now);
            AimingData aimingData = getAimingDataByAngle(vs.getDirection());
            Enemy enemy = aimingData.getTarget();
            if (p.x >= 0 && p.x <= getBattleFieldWidth() && p.y >= 0 && p.y <= getBattleFieldHeight()) {
                Enemy e = vs.getTarget();
                boolean hit = false;

                if (p.distance(e) < TANK_SIZE / 2)
                    hit = true;
                else {
                    Point.Double pp = vs.getPosition(now - 0.1);
                    if (pp.distance(e) < p.distance(e))
                        for (double t = now - 1; t < now; t += 0.1) {
                            pp = vs.getPosition(t);
                            if (pp.distance(e) < TANK_SIZE / 2) {
                                hit = true;
                                break;
                            }
                        }
                }

                if (hit) {
                    vs.getGunner().hit(aimingData.getTarget());
                    enemy.hit(aimingData.getKdPoint());
                }
                return !hit;
            }
            enemy.miss(aimingData.getKdPoint());

            return false;
        }).collect(Collectors.toList());

        vShells = newVShells;
    }

    private void updateWaves() {
        List<Wave> newWaves = waves.stream().filter(w -> {
            Point.Double p = w.getPosition(now);
            return p.x >= 0 && p.x <= getBattleFieldWidth() && p.y >= 0 && p.y <= getBattleFieldHeight()
                && w.distance(getCurrentPoint()) >= w.distance(p);
        }).collect(Collectors.toList());

        waves = newWaves;
    }

    private void updateLeftRightEnemies() {
        List<Enemy> sEnemies = enemies.values().stream().filter(e -> e.alive).collect(Collectors.toList());
        sEnemies.sort(new Comparator<Enemy>() {
            @Override
            public int compare(final Enemy e1, final Enemy e2) {
                if (e1.angle < e2.angle)
                    return -1;
                if (e1.angle > e2.angle)
                    return 1;
                return 0;
            }
        });

        Enemy prev = mostRight = sEnemies.get(0);
        mostLeft = sEnemies.get(sEnemies.size() - 1);
        double ba = abs(normalRelativeAngle(mostLeft.angle) - normalRelativeAngle(mostRight.angle));
        for (Enemy enemy : sEnemies) {
            final double a = enemy.angle - prev.angle;
            if (a > ba) {
                mostRight = enemy;
                mostLeft = prev;
                ba = a;
            }
            prev = enemy;
        }
    }

    private void moveEnemy(Enemy e, long now) {
        if (e.lastUpdate >= now || e.getEnergy() == 0)
            return;
        long time = now - e.lastUpdate;
        for (long i = 0; i < time; i++) {
            e.velocity = checkMinMax(e.velocity + e.accel, e.vMin, e.vMax);
            e.direction += min(abs(e.rotationRate), getTurnRateRadians(e.velocity)) * signum(e.rotationRate);
            try {
                double x = ensureXInBatleField(e.x + e.velocity * cos(e.direction));
                double y = ensureYInBatleField(e.y + e.velocity * sin(e.direction));
                e.x = x;
                e.y = y;
            } catch (Exception ex) {
                // HitWall
            }
        }
        e.angle = normalAbsoluteAngle(getAngle(getCurrentPoint(), e));
        e.lastUpdate = now;
    }

    private void updatePositions() {
        double totalEnergy = getEmenmiesEnergy();
        double x = 0;
        double y = 0;
        double d = Double.POSITIVE_INFINITY;

        target = null;
        for (Enemy e : enemies.values()) {
            moveEnemy(e, now);

            //out.printf("%s x=%.0f, y=%.0f, nrj=%.0f\n", e.name, e.getX(), e.getY(), e.getEnergy());
            x += e.x * (e.getEnergy()+0.001);
            y += e.y * (e.getEnergy()+0.001);

            double od = getCurrentPoint().distance(e);
            if ((od < d || e.getEnergy() == 0) && e.alive && e.fEnergy >= -0.0001) {
                // target closet alive opponent or 0 energy
                target = e;
                d = od;
            }
            //out.printf("target is %s\n", target != null ? target.getName()+ "( " + target.getEnergy() + " , "+target.fEnergy + " )": "None");
            e.angle = normalAbsoluteAngle(getAngle(getCurrentPoint(), e));
        }

        if (totalEnergy == 0)
            return;

        unsafePosition = new Point.Double(x / totalEnergy, y / totalEnergy);
        safePosition = getOppositeFarPoint(unsafePosition);
        //out.printf("unsafe x=%.0f, y=%.0f\n", unsafePosition.getX(), unsafePosition.getY());
        //out.printf("safeposition x=%.0f, y=%.0f\n",safePosition.getX(), safePosition.getY());
    }

    private double getEmenmiesEnergy() {
        return enemies.values().stream().mapToDouble(e -> e.getEnergy()).sum();
    }

    private void computeSafePosition() {
        double x = 0;
        double y = 0;
        double div;

        if (aliveCount() > 1) {
            div = getEmenmiesEnergy();

            for (Enemy e : enemies.values()) {
                //out.printf("%s x=%.0f, y=%.0f, nrj=%.0f\n", e.name, e.getX(), e.getY(), e.getEnergy());
                x += e.x * (e.getEnergy() + 0.001);
                y += e.y * (e.getEnergy() + 0.001);
            }
        } else {
            div = waves.stream().mapToDouble(w -> w.getPower()).sum();
            for (Wave w : waves) {
                Point.Double p = w.getPosition(now);
                Double c = w.getPower();
                x += p.x * c;
                y += p.y * c;
            }
        }

        unsafePosition = new Point.Double(x/div, y/div);
        safePosition =  getOppositeFarPoint(unsafePosition);
    }

    private double getTurn2Safe() {
        if (safePosition == null)
            return 0;

        double sa = getAngle(getCurrentPoint(), safePosition);
        double ra = trigoAngle(getHeadingRadians());

        if (abs(normalRelativeAngle(sa - ra)) <= (PI / 2)) {
            forward = 1;
            return normalRelativeAngle(sa - ra);
        }

        forward = -1;
        return normalRelativeAngle(oppositeAngle(sa) - ra);
    }

    private double getSafeAhead() {
        if (safePosition == null || getCurrentPoint() == null)
            return 0;

        return forward * safePosition.distance(getCurrentPoint());
    }

    private void fireTargetIfPossible(double fire) {
        if (aimingData == null || getGunHeat() > 0 || target.alive == false || fire == 0)
            return;

        if (getCurrentPoint().distance(aimingData.getFiringPosition()) * sin(abs(getGunTurnRemainingRadians())) > FIRE_TOLERANCE ||
            abs(getGunTurnRemainingRadians()) > PI / 2) {
            return;
        }

        setFire(fire);
        //out.printf("target is %s(x=%.0f, y = %.0f) energy(%.02f, %.02f)\n", target.getName(), target.getX(), target.getY(), target.getEnergy(), target.fEnergy);
        //out.printf("Gunner is %s fire at x=%.0f, y = %.0f, turnRemain= %f\n", aimingData.getGunner().getName(),
        //aimingData.getFiringPosition().getX(), aimingData.getFiringPosition().getY(), getGunTurnRemainingRadians());
        lastFireTime = now;
        aimingData.getGunner().fire(target);
        target.fEnergy -= getBulletDamage(fire);

        aimingData.setAngle(getGunHeadingRadians());
        aimDatas.add(aimingData);

        fireVirtualShell();
        //out.printf("Shooting %s, power %.02f, gunner %s confidence=%.02f\n",
        //target.name, fire, aimingData.getGunner().getName(), aimingData.getConfidence());
    }

    private void fireVirtualShell() {
        gunners.values().forEach(gunner -> {
            if (gunner != aimingData.getGunner()) {
                final AimingData ad = gunner.aim(aimingData.getTarget());
                if (ad != null) {
                    aimDatas.add(ad);
                    gunner.fire(aimingData.getTarget());
                    vShells.add(new VShell(
                        getCurrentPoint(),
                        Rules.getBulletSpeed(ad.getFirePower()),
                        ad.getAngle(),
                        now,
                        ad.getTarget(),
                        ad.getGunner()));
                }
            }
        });
    }

    private double getTurn2Targert() {
        aimingData = null;

        if (target == null)
            return 0;

        for (Gunner gunner : gunners.values()) {
            AimingData temp = gunner.aim(target);
            if (aimingData == null ||
                (temp != null && temp.getGunner().hitRate(target) > aimingData.getGunner().hitRate(target)))
                    aimingData = temp;
        }
        if (aimingData != null) {
            fire = aimingData.getFirePower();
            return computeTurnGun2Target(this, aimingData.getFiringPosition());
        }

        fire = 0;
        return 0;
    }

    public long aliveCount() {
        return enemies.values().stream().filter(e -> e.alive).count();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    private void resetRoundStat() {
        gunners.values().stream().forEach(gunner -> {
            out.printf("%s hitrate = %.0f%% / %d\n", gunner.getName(), gunner.hitRate() * 100, gunner.fireCount());
            gunner.resetStat();
            enemies.values().stream().forEach(enemy -> {
                out.printf("%s -> %s hitrate = %.0f%% / %d\n", gunner.getName(), enemy.getName(), gunner.hitRate(enemy) * 100, gunner.fireCount(enemy));
                gunner.resetStat(enemy);
            });
        });
        enemies.values().stream().forEach(enemy -> enemy.rebuildKDTree());
    }

    private Point.Double getBorderPoint(Point.Double p, double angle) {
        double a = normalRelativeAngle(angle);
        if (a == 0)
            return new Point.Double(getBattleFieldWidth() - BORDER_OFFSET, p.y);
        if (a == PI / 2)
            return new Point.Double(p.x, getBattleFieldHeight() - BORDER_OFFSET);
        if (abs(a) == PI)
            return new Point.Double(BORDER_OFFSET, p.y);
        if (a == -PI / 2)
            return new Point.Double(p.y, BORDER_OFFSET);

        double dx;
        double dy;

        if (0 < a && a < PI / 2) {
            dx = (getBattleFieldWidth() - BORDER_OFFSET - p.x) / cos(a);
            dy = (getBattleFieldHeight() - BORDER_OFFSET - p.y) / sin(a);
        } else if (PI / 2 < a && a < PI) {
            dx = (p.x - BORDER_OFFSET) / cos(a);
            dy = (getBattleFieldHeight() - BORDER_OFFSET - p.y) / sin(a);
        } else if ((PI < a && a < 3 * PI / 2) || (-PI < a && a < -PI / 2)) {
            dx = (p.x - BORDER_OFFSET) / cos(a);
            dy = (p.y - BORDER_OFFSET) / sin(a);
        } else {
            dx = (getBattleFieldWidth() - BORDER_OFFSET - p.x) / cos(a);
            dy = (p.y - BORDER_OFFSET) / sin(a);
        }

        // out.printf("a=%.02f dx=%.02f dy=%.02f\n", a, dx ,dy);

        double d = abs((abs(dx) >= abs(dy)) ? dy : dx);
        return new Point.Double(p.x + d * cos(a), p.y + d * sin(a));
    }

    public Point.Double getOppositeFarPoint(Point.Double point) {
        double a = oppositeAngle(getAngle(BATTLE_FIELD_CENTER, point));
        double c = acos(getBattleFieldHeight() / getBattleFieldWidth());
        double x, y;
        if (a < c && a > -c) { // Right
            x = getBattleFieldWidth() - BORDER_OFFSET;
            y = (sin(a) * (getBattleFieldHeight() - 2 * BORDER_OFFSET) + getBattleFieldHeight()) / 2;
        } else if (a > c && a < PI - c) { // top
            x = (cos(a) * (getBattleFieldWidth() - 2 * BORDER_OFFSET) + getBattleFieldWidth()) / 2;
            y = getBattleFieldHeight() - BORDER_OFFSET;
        } else if (a < -c && a >= -PI + c) { // bottom
            x = (cos(a) * (getBattleFieldWidth() - 2 * BORDER_OFFSET) + getBattleFieldWidth()) / 2;
            y = BORDER_OFFSET;
        } else { // left
            x = BORDER_OFFSET;
            y = (sin(a) * getBattleFieldHeight() + getBattleFieldHeight()) / 2;
        }
        return new Point.Double(x, y);
    }

    public Point.Double getCurrentPoint() {
        return new Point.Double(getX(), getY());
    }

    public double ensureXInBatleField(double x) throws Exception {
        double nx = max(TANK_SIZE / 2, min(getBattleFieldWidth() - TANK_SIZE / 2, x));
        if (abs(nx - x) > 2) throw new Exception("hit wall x");
        return nx;
    }

    public double ensureYInBatleField(double y) throws Exception {
        double ny = max(TANK_SIZE / 2, min(getBattleFieldHeight() - TANK_SIZE / 2, y));
        if (abs(ny - y) > 2) throw new Exception("hit wall y");
        return ny;
    }

    public double conerDistance(Point.Double p) {
        return sqrt(pow(min(p.x, BATTLE_FIELD_CENTER.x * 2 - p.x), 2) +
            pow(min(p.y, BATTLE_FIELD_CENTER.y * 2 - p.y), 2));
    }

    private void setupGunners() {
        if (gunners.values().size() == 0) {
            putGunner(new HeadOnGunner(this));
            putGunner(new CircularGunner(this));
            putGunner(new NearestNeighborGunner(this));
        }
    }

    private void putGunner(Gunner gunner) {
        gunners.put(gunner.getName(), gunner);
    }

    private AimingData getAimingDataByAngle(double angle) {
        Optional<AimingData> opt = aimDatas.stream().filter(aimingData -> aimingData.getAngle() == angle).findFirst();

        return opt.isPresent() ? opt.get() : null;
    }
}