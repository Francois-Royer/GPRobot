package tankbase;

public class RobotCache {
    double x;
    double y;
    double headingRadians;
    double gunHeadingRadians;
    double turnRemaining;
    double velocity;
    double gunHeat;
    double energy;
    int others;

    long date;

    public RobotCache(double x, double y, double headingRadians, double gunHeadingRadians, double turnRemaining, double velocity,
                      double gunHeat, double energy, int others, long date) {
        this.x = x;
        this.y = y;
        this.headingRadians = headingRadians;
        this.gunHeadingRadians = gunHeadingRadians;
        this.turnRemaining = turnRemaining;
        this.velocity = velocity;
        this.gunHeat = gunHeat;
        this.energy = energy;
        this.others = others;
        this.date = date;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getHeadingRadians() {
        return headingRadians;
    }

    public double getGunHeadingRadians() {
        return gunHeadingRadians;
    }

    public double getTurnRemaining() {
        return turnRemaining;
    }

    public double getVelocity() {
        return velocity;
    }

    public double getGunHeat() {
        return gunHeat;
    }

    public double getEnergy() {
        return energy;
    }

    public int getOthers() {
        return others;
    }

    public long getDate() { return date; }
}
