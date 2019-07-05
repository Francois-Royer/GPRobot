package sampleex;

import robocode.*;
import static robocode.Rules.*;
import java.awt.Color;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class %s extends AdvancedRobot {
    class Opponent {
        double x;
        double y;
        double energy;

        public Opponent(double x, double y, double energy) {
            this.x = x;
            this.y = y;
            this.energy = energy;
        }
    }

    Map<String, Opponent>opponents = new HashMap<>();

    public void run() {

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        setColors(Color.red,Color.blue,Color.green);
        while(true)
            turnRadarRight(Double.POSITIVE_INFINITY);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        updateOpponents(e);

        // --- PHENOME 1 ---
        double ahead = %s;
        // --- PHENOME 2 ---
        double turnRadarRight = %s;
        // --- PHENOME 3 ---
        double turnRight = %s;
        // --- PHENOME 4 ---
        double turnGunRight = %s;
        // --- PHENOME 5 ---
        double fire = %s;

        //out.println("ahead=" +ahead+ ", fire=" + fire);
        //out.println("turnRight=" +turnRight+ ", turnGunRight=" + turnGunRight + ", turnRadarRight=" + turnRadarRight);
        robotSetActions(ahead, turnRight, turnGunRight, turnRadarRight, fire);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // --- PHENOME 6 ---
        double ahead=%s;
        // --- PHENOME 7 ---
        double turnRadarRight=%s;
        // --- PHENOME 8 ---
        double turnRight=%s;
        // --- PHENOME 9 ---
        double turnGunRight=%s;
        // --- PHENOME 10 ---
        double fire=%s;

        //out.println("ohbb ahead=" +ahead+ ", fire=" + fire);
        //out.println("ohbb turnRight=" +turnRight+ ", turnGunRight=" + turnGunRight + ", turnRadarRight=" + turnRadarRight);
        robotSetActions(ahead,turnRight,turnGunRight,turnRadarRight,fire);
    }

    private void robotSetActions(double ahead, double turnRight, double turnGunRight, double turnRadarRight, double fire) {
        setAhead(ahead);
        setTurnRightRadians(turnRight);
        setTurnGunRightRadians(turnGunRight);
        setTurnRadarRightRadians(turnRadarRight);
        setFire(fire);
    }

    public void onRobotDeath(RobotDeathEvent event) {
        opponents.remove(event.getName());
    }

    private void updateOpponents(ScannedRobotEvent e) {
        double x = getX() + e.getDistance() * Math.cos(e.getHeadingRadians());
        double y = getY() + e.getDistance() * Math.sin(e.getHeadingRadians());
        opponents.put(e.getName(), new Opponent(x, y, e.getEnergy()));
    }

    private double getOppenentsEnergy() {
        double sum=0;
        for (Opponent o: opponents.values()) sum += o.energy;
        return sum;
    }

    private double getOpponentsX() {
        double sum=0;
        for (Opponent o: opponents.values()) sum += o.x * o.energy;
        return sum / getOppenentsEnergy() / getOthers();
    }

    private double getOpponentsY() {
        double sum=0;
        for (Opponent o: opponents.values()) sum += o.y * o.energy;
        return sum / getOppenentsEnergy() / getOthers();
    }
}