package sampleex;

import robocode.*;

import static robocode.util.Utils.*;
import java.awt.Color;

import static java.lang.Math.*;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class GPBase extends AdvancedRobot {
    public static double TANK_SIZE=36;

    Point.Double unsafePosition;
    Point.Double safePosition;
    Point.Double center;

    Point.Double closestPred;

    public double mostLeft;
    public double mostRight;

    public double forward=1;
    public double scandirection=1;
    public double offset =TANK_SIZE *3/2;

    public double turnLeft = 0;
    public double turnGunLeft = 0;
    public double turnRadarLeft = 0;
    public double ahead = 0;
    public double fire = 0;

    ScannedRobotEvent sre = null;

    Map<String, Opponent>opponents = new HashMap<>();
    Opponent closest;
    Opponent lastscanned;


    public void run() {

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        center = new Point.Double(getBattleFieldWidth()/2, getBattleFieldHeight()/2);
        setColors(Color.red,Color.blue,Color.green);

        while(true) {
            doTurn();
            execute();
        }

    }

    public void onScannedRobot(ScannedRobotEvent e) {
        this.sre = e;
        updateOpponents(e);
        doTurn();
    }

    void doGP() {

    }

    public void doTurn() {
        updatePositions();
        double ra = trigoAngle(getRadarHeadingRadians());
        /*if (getOthers()<=opponents.size()) {

               if (ra >= mostLeft) scandirection = -1;
               if (ra <= mostRight) scandirection = 1;
               turnRadarLeft = PI * 2 *scandirection;

        } else*/
            turnRadarLeft = PI * 2;

        turnLeft = getSafeTurn();
        ahead = getSafeAhead();
        turnGunLeft = getClosestTurn();
        fire = fireClosestIfPossible();
        doGP();
        robotSetActions();
    }

    public void onRobotDeath(RobotDeathEvent event) {
        opponents.remove(event.getName());
    }

    public void onPaint(Graphics2D g) {
        drawCircle(g, Color.RED, unsafePosition);
        drawCircle(g, Color.YELLOW, center);
        drawCircle(g, Color.GREEN, safePosition);

        for (Opponent o: opponents.values())
            drawCircle(g, (o == closest) ? Color.CYAN :Color.PINK, getPoint(o));

        if (closestPred != null)
            drawCircle(g, Color.MAGENTA, closestPred);
    }
    //public void onHitWall(HitWallEvent e) { forward *= -1; }
    //public void onHitRobot(HitRobotEvent e) { forward *= -1; }

    class Opponent {
        String name;
        double x;
        double y;
        double energy;
        double velocity;
        double direction;
        double rotationRate;
        double accel;
        long lastUpdate;
        double vMax;

        public Opponent(String name, double x, double y, double energy, double velocity, double direction, long lastUpdate) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.energy = energy;
            this.velocity = velocity;
            this.direction = direction;
            Opponent old = opponents.get(name);
            if (old != null && lastUpdate != old.lastUpdate) {
                this.rotationRate = normalRelativeAngle( direction - old.direction ) / ( lastUpdate - old.lastUpdate);
                if (velocity == 0) accel = 0;
                else
                    accel = velocity - old.velocity;
                this.vMax = max(old.vMax, abs(velocity));
            } else {
                this.rotationRate = 0;
                this.vMax = abs(velocity);
            }
            this.lastUpdate = lastUpdate;
            out.printf("velocity=%.02f vmax=%.02f\n", this.velocity, this.vMax);
        }
    }

    private void robotSetActions() {
        /*out.printf("robotSetActions turnRadarLeft=%.2f turnLeft=%.2f ahead=%.2f turnGunLeft=%.2f fire=%.2f\n",
            turnRadarLeft, turnLeft, ahead, turnGunLeft, fire);*/

        setAhead(ahead);
        setTurnLeftRadians(turnLeft);
        setTurnGunLeftRadians(turnGunLeft);
        setTurnRadarLeftRadians(turnRadarLeft);
        if (fire > 0)
            setFire(fire);
        /*if (random() >.9)
            setMaxVelocity(Rules.MAX_VELOCITY/2);
        else
            setMaxVelocity(Rules.MAX_VELOCITY);*/
    }

    private void updateOpponents(ScannedRobotEvent e) {
        double angle = trigoAngle(getHeadingRadians() + e.getBearingRadians());
        double direction = trigoAngle(e.getHeadingRadians());
        double x = getX() + e.getDistance() * cos(angle);
        double y = getY() + e.getDistance() * sin(angle);

        Opponent old = opponents.get(e.getName());
        opponents.put(e.getName(), new Opponent(e.getName(), x, y, e.getEnergy(), e.getVelocity(), direction, getTime()));
    }


    private void moveOponent(Opponent o, long now) {
        if (o.lastUpdate >= now) return;
        long time = now-o.lastUpdate;
        for (long i=0; i<time; i++) {
            o.x = ensureXInBatleField(o.x  + o.velocity * cos(o.direction));
            o.y = ensureYInBatleField(o.y + o.velocity * sin(o.direction));
            o.direction += o.rotationRate;
            o.velocity = checkVelocity(o.velocity + o.accel, o.vMax);
        }
        o.lastUpdate=now;
    }

    private void updatePositions() {
        long now = getTime();
        double totalEnergy = getOpponentsEnergy();
        double x=0;
        double y=0;
        double d=Double.POSITIVE_INFINITY;

        mostLeft=-PI;
        mostRight=PI;
        for (Opponent o: opponents.values()) {
            moveOponent(o, now);
            x += o.x * o.energy;
            y += o.y * o.energy;

            double od = getCurrentPoint().distance(getPoint(o));
            if (od < d) {
                closest = o;
                d=od;
            }
            double a = getAngle(getCurrentPoint(), getPoint(o));
            if (a>mostLeft)
                mostLeft = a;
            if (a<mostRight)
                mostRight = a;

        }

        unsafePosition = new Point.Double(x/totalEnergy, y/totalEnergy);

        double a = oppositeAngle(getAngle(center, unsafePosition));

        double c = acos(getBattleFieldHeight()/getBattleFieldWidth());

        if (a < c && a> -c) { // Right
            x = getBattleFieldWidth()-offset;
            y = (sin(a)*(getBattleFieldHeight()-2*offset) + getBattleFieldHeight())/2;
        } else if (a>c && a<PI-c) { // top
            x = (cos(a)*(getBattleFieldWidth()-2*offset) + getBattleFieldWidth())/2;
            y = getBattleFieldHeight()-offset;
        } else if (a< -c && a>=-PI+c) { // bottom
            x = (cos(a)*(getBattleFieldWidth()-2*offset) + getBattleFieldWidth())/2;
            y = offset;
        } else { // left
            x = offset;
            y=(sin(a)*getBattleFieldHeight()+getBattleFieldHeight())/2;
        }
        safePosition=new Point.Double( x, y);
    }

    private double getOpponentsEnergy() {
        double sum = 0;
        for (Opponent o : opponents.values()) sum += o.energy;
        if (sum == 0)
            sum = 0.00000001; // Avoid zero div when last is disabled

        return sum;
    }

    // -PI -> PI
    private double getAngle(Point.Double s, Point.Double d) {
        double a = acos((d.x-s.x)/s.distance(d));
        if (d.y < s.y)
            a= 2*PI-a;
        return normalRelativeAngle(a);
    }

    // -PI -> PI
    public double oppositeAngle(double a) {
        return normalRelativeAngle(a+PI);
    }

    // convert robocode angle to trigonometric angle
    public double trigoAngle(double roboAngle) {
        return normalRelativeAngle(PI/2-roboAngle);
    }

    private double getSafeTurn() {
        double sa = getAngle(getCurrentPoint(), safePosition);
        double ra = trigoAngle(getHeadingRadians());

        if (abs(normalRelativeAngle(sa-ra)) <= (PI / 2)) {
            forward = 1;
            return normalRelativeAngle(sa-ra);
        }

        forward = -1;
        return normalRelativeAngle(oppositeAngle(sa) - ra);
    }
    private double getSafeAhead() {
        return forward * safePosition.distance(getCurrentPoint());
    }

    private double fireClosestIfPossible() {
        if (closestPred == null || abs(getGunTurnRemainingRadians()) > 0.00001)
            return 0;

        return getFirePower(closestPred);
    }

    private double getFirePower(Point.Double p) {
        return Math.min(1000 / getCurrentPoint().distance(p), 3);
    }

    private double getClosestTurn() {
        if (closest == null) {
            closestPred = null;
            return 0;
        }

        for (int i=0; i<5 ; i++) {
            closestPred = getPoint(closest);
            double firePower = getFirePower(closestPred);
            double bulletSpeed = Rules.getBulletSpeed(firePower);
            long time = (long)(getCurrentPoint().distance(closestPred) / bulletSpeed);

            double direction = closest.direction;
            double velocity = closest.velocity;

            double v = closest.velocity;

            for (long t=0; t<time; t++) {
                closestPred.x = (int) ensureXInBatleField(closestPred.x + v * cos(direction));
                closestPred.y = (int) ensureYInBatleField(closestPred.y + v * sin(direction));
                direction += closest.rotationRate;
                v = checkVelocity(v+ closest.accel, closest.vMax);
            }
            time = (long)(getCurrentPoint().distance(closestPred) / bulletSpeed);
        }

        double ga = trigoAngle(getGunHeadingRadians());
        double ta = getAngle(getCurrentPoint(),closestPred);

        if (abs(ta-ga) <= PI) {
            return ta - ga;
        }

        return ga-ta;
    }

    private Point.Double getCurrentPoint() {
        return new Point.Double(getX(),getY());
    }

    private Point.Double getPoint(Opponent o) {
        return new Point.Double( o.x, o.y);
    }

    public double ensureXInBatleField(double x) {
        return max(TANK_SIZE/2, min(getBattleFieldWidth()-TANK_SIZE/2, x));
    }

    public double ensureYInBatleField(double y) {
        return max(TANK_SIZE/2, min(getBattleFieldHeight()-TANK_SIZE/2, y));
    }

    public double checkVelocity(double v, double max) {
        return max(-max, min(max, v));
    }

    private void drawCircle(Graphics2D g, Color c, Point.Double p) {
        int d=10;
        g.setColor(c);
        g.fillArc((int) p.x-d/2, (int) p.y-d/2, d, d, 0, 360);
    }
}