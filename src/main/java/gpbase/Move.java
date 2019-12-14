package gpbase;

public class Move {
    private double[] kdpoint; // Point for kdtree
    private double turn, velocity;
    private long duration;

    public Move(double[] kdpoint, double turn, double velocity, long duration) {
        this.kdpoint = kdpoint;
        this.turn = turn;
        this.velocity = velocity;
        this.duration = duration;
    }

    public double[] getKdpoint() {
        return kdpoint;
    }

    public double getTurn() {
        return turn;
    }

    public double getVelocity() {
        return velocity;
    }

    public long getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return Double.toString(turn) + ',' + velocity + ',' + duration + ',';
    }
}
