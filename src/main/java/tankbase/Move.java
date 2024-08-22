package tankbase;

public class Move {
    private final double[] patternKdPoint; // Point for kdtree
    private final double[] surferKdPoint; // Point for kdtree
    private final double turn;
    private final double velocity;
    private final long duration;

    public Move(double[] patternKdPoint, double[] surferKdPoint, double turn, double velocity, long duration) {
        this.patternKdPoint = patternKdPoint;
        this.surferKdPoint = surferKdPoint;
        this.turn = turn;
        this.velocity = velocity;
        this.duration = duration;
    }

    public double[] getPatternKdPoint() {
        return patternKdPoint;
    }

    public double[] getSurferKdPoint() {
        return surferKdPoint;
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
