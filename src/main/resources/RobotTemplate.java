package sampleex;

import robocode.*;
import static robocode.Rules.*;
import java.awt.Color;

public class %s extends AdvancedRobot {

    public void run() {

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        setColors(Color.red,Color.blue,Color.green);
        while(true) {
            if (getRadarTurnRemainingRadians() == 0)
               turnRadarRight(360);
            else
               execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // --- PHENOME 1 ---
        double ahead = %s - getDistanceRemaining();
        // --- PHENOME 2 ---
        double turnRight = %s - getTurnRemainingRadians();
        // --- PHENOME 3 ---
        double turnGunRight = %s - getGunTurnRemainingRadians();
        // --- PHENOME 4 ---
        double turnRadarRight = %s - getRadarTurnRemainingRadians();
        // --- PHENOME 5 ---
        double fire = %s;

        //out.println("ahead=" +ahead+ ", fire=" + fire);
        //out.println("turnRight=" +turnRight+ ", turnGunRight=" + turnGunRight + ", turnRadarRight=" + turnRadarRight);
        robotSetActions(ahead, turnRight, turnGunRight, turnRadarRight, fire);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // --- PHENOME 6 ---
        double ahead = %s - getDistanceRemaining();
        // --- PHENOME 7 ---
        double turnRight = %s - getTurnRemainingRadians();

        //out.println("ohbb ahead=" +ahead+ ", fire=" + fire);
        //out.println("ohbb turnRight=" +turnRight+ ", turnGunRight=" + turnGunRight + ", turnRadarRight=" + turnRadarRight);
        setAhead(ahead);
        setTurnRightRadians(turnRight);
    }

    public void onHitRobot(HitRobotEvent e) {
        // --- PHENOME 8 ---
        double ahead = %s - getDistanceRemaining();
        // --- PHENOME 9 ---
        double turnRight = %s - getTurnRemainingRadians();
        // --- PHENOME 10 ---
        double turnGunRight = %s - getGunTurnRemainingRadians();
        // --- PHENOME 11 ---
        double turnRadarRight = %s - getRadarTurnRemainingRadians();
        // --- PHENOME 12 ---
        double fire = %s;

        //out.println("ohbb ahead=" +ahead+ ", fire=" + fire);
        //out.println("ohbb turnRight=" +turnRight+ ", turnGunRight=" + turnGunRight + ", turnRadarRight=" + turnRadarRight);
        robotSetActions(ahead, turnRight, turnGunRight, turnRadarRight, fire);
    }

    private void robotSetActions(double ahead, double turnRight, double turnGunRight, double turnRadarRight, double fire) {
        setAhead(ahead);
        setTurnRightRadians(turnRight);
        setTurnGunRightRadians(turnGunRight);
        setTurnRadarRightRadians(turnRadarRight);
        setFire(fire);
    }
}