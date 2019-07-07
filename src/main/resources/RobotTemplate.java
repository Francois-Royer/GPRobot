package sampleex;

import robocode.*;
import static robocode.Rules.*;
import java.awt.Color;

import static java.lang.Math.*;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class %s extends AdvancedRobot {
    Point unsafePosition;
    Point safePosition;
    Point center;
    Point closestPred;

    double forward=1;
    double offset =54;

    double turnRadarRight = 0;
    double turnRight = 0;
    double ahead = 0;
    double turnGunRight = 0;
    double fire = 0;
    ScannedRobotEvent e = null;

    public void run() {

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        center = new Point((int)getBattleFieldWidth()/2, (int)getBattleFieldHeight()/2);
        setColors(Color.red,Color.blue,Color.green);
        while(true) {
            updatePositions();
            if (e != null){
                // --- PHENOME 1 ---
                turnRadarRight=%s;
                // --- PHENOME 2 ---
                turnRight = %s;
                // --- PHENOME 3 ---
                ahead = %s;
                // --- PHENOME 4 ---
                turnGunRight = %s;
                // --- PHENOME 5 ---
                fire = %s;
                e=null;
            }
            if (getRadarTurnRemainingRadians() == 0)
                turnRadarRight = PI *2;

            if (getOthers()>1){
                turnRight=getSafeTurn();
                ahead=getSafeAhead();
                turnGunRight=getClosestTurn();
                fire=fireClosestIfPossible();
            }

            robotSetActions();
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        this.e = e;
        updateOpponents(e);
    }

    public void onRobotDeath(RobotDeathEvent event) {
        opponents.remove(event.getName());
        updatePositions();
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
        double x;
        double y;
        double energy;
        double speed;
        double direction;
        long lastUpdate;

        public Opponent(double x, double y, double energy, double speed, double direction, long lastUpdate) {
            this.x = x;
            this.y = y;
            this.energy = energy;
            this.speed = speed;
            this.direction = direction;
            this.lastUpdate = lastUpdate;
        }
    }

    Map<String, Opponent>opponents = new HashMap<>();
    Opponent closest;

    private void robotSetActions() {
        setAhead(ahead);
        setTurnRightRadians(turnRight);
        setTurnGunRightRadians(turnGunRight);
        setTurnRadarRightRadians(turnRadarRight);
        if (fire > 0)
            setFire(fire);
    }

    private void updateOpponents(ScannedRobotEvent e) {
        double angle = (e.getBearingRadians() + getHeadingRadians()) * -1 + Math.PI/2;
        double x = getX() + e.getDistance() * cos(angle);
        double y = getY() + e.getDistance() * sin(angle);

        opponents.put(e.getName(), new Opponent(x, y, e.getEnergy(), e.getVelocity(), mod2PI(PI/2 - e.getHeadingRadians()), getTime()));
        updatePositions();
    }


    private void moveOponent(Opponent o, long now) {
        if (o.lastUpdate >= now) return;
        Double d  =  o.speed * (now-o.lastUpdate);
        o.x += d*cos(o.direction);
        o.y += d*sin(o.direction);
        o.lastUpdate=now;
    }

    private void updatePositions() {
        long now = getTime();
        double totalEnergy = getOpponentsEnergy();
        double x=0;
        double y=0;
        double d=Double.POSITIVE_INFINITY;

        for (Opponent o: opponents.values()) {
            moveOponent(o, now);
            x += o.x * o.energy;
            y += o.y * o.energy;

            double od = getCurrentPoint().distance(getPoint(o));
            if (od < d) {
                closest = o;
                d=od;
            }
        }

        unsafePosition = new Point((int) (x/totalEnergy), (int) (y/totalEnergy));

        double a = opposite(getAngle(center, unsafePosition));

        //out.println(String.format("a=%%.2f ", a));

        double c = acos(getBattleFieldHeight()/getBattleFieldWidth());

        if (a < c && a> -c) { // right
            x = getBattleFieldWidth()-offset;
            y = (sin(a)*(getBattleFieldHeight()-2*offset) + getBattleFieldHeight())/2;
        } else if (a>c && a<=PI-c) { // top
            x = (cos(a)*(getBattleFieldWidth()-2*offset) + getBattleFieldWidth())/2;
            y = getBattleFieldHeight()-offset;
        } else if (a< -c && a>=-PI+c) { // bottom
            x = (cos(a)*(getBattleFieldWidth()-2*offset) + getBattleFieldWidth())/2;
            y = offset;
        } else { // left
            x = offset;
            y=(sin(a)*getBattleFieldWidth()+getBattleFieldHeight())/2;
        }
        safePosition=new Point((int) x,(int) y);
        //out.println(String.format("safe x=%%d y=%%d", safePosition.x, safePosition.y));
    }

    private double getOpponentsEnergy() {
        double sum=0;
        for (Opponent o: opponents.values()) {
            if (o.energy == 0)
                o.energy = 0.00000001; // Avoid zero div
            sum += o.energy;
        }
        return sum;
    }

    // -PI -> PI
    private double getAngle(Point s, Point d) {
        double a = acos((d.x-s.x)/s.distance(d));
        if (d.y < s.y)
            a= 2*PI-a;
        return mod2PI(a);
    }

    // -PI -> PI
    private double opposite(double a) {
        return mod2PI(a+PI);
    }

    // -PI -> PI
    private double mod2PI(double a) {
        double o = a;
        while (o>PI)
            o-=2*PI;
        while (o<-PI)
            o+=2*PI;
        return o;
    }

    private double getSafeTurn() {
        double sa = getAngle(getCurrentPoint(), safePosition);
        double ra = mod2PI(PI/2 - getHeadingRadians());

        //out.println(String.format("sa=%%.2f ra=%%.2f, heading=%%.2f", sa, ra, getHeadingRadians()));

        if (abs(ra - sa) < (PI / 2) || (abs(ra - sa) >(PI *3/2))) {
            forward = 1;
            return mod2PI(ra - sa);
        }

        forward = -1;
        return ra - opposite(sa);
    }
    private double getSafeAhead() {
        return forward * safePosition.distance(getCurrentPoint());
    }

    private Point getCurrentPoint() {
        return new Point((int) getX(),(int) getY());
    }

    private Point getPoint(Opponent o) {
        return new Point((int) o.x,(int) o.y);
    }

    private void drawCircle(Graphics2D g, Color c, Point p) {
        int d=10;
        g.setColor(c);
        g.fillArc(p.x-d/2, p.y-d/2, d, d, 0, 360);
    }

    private double fireClosestIfPossible() {
        if (closestPred == null ||
            closestPred.x < 0 || closest.x>getBattleFieldWidth() ||
            closest.y < 0 || closest.y>getBattleFieldHeight() ||
            getGunTurnRemainingRadians() > 0.001)
            return 0;

        return fireClosest();
    }

    private double fireClosest() {
        return Math.min(1000 / getCurrentPoint().distance(getPoint(closest)), 3);
    }

    private double getClosestTurn() {
        if (closest == null)
            return 0;

        // calculate firepower based on distance
        double firePower = fireClosest();

        // calculate speed of bullet
        double bulletSpeed = Rules.getBulletSpeed(firePower);

        // distance = rate * time, solved for time
        long time = (long)(getCurrentPoint().distance(getPoint(closest)) / bulletSpeed);

        double x=-1;
        double y=-1;

        for (int i=0; i<3 ; i++) {

            Double d = closest.speed * time;

            x = closest.x + d * cos(closest.direction);
            y = closest.y + d * sin(closest.direction);
            time = (long)(getCurrentPoint().distance(new Point((int)x, (int) y)) / bulletSpeed);
        }

        if (x < 0 || x>getBattleFieldWidth() || y < 0 || y>getBattleFieldHeight()) {
            closestPred = null;
            return 0;
        }

        closestPred = new Point((int) x, (int) y);

        double ga = mod2PI(PI/2 - getGunHeadingRadians());
        double ta = getAngle(getCurrentPoint(), new Point((int) x, (int) y));

        if (abs(ga - ta) < PI) {
            return ga - ta;
        }

        return mod2PI(ta-ga);
    }
}