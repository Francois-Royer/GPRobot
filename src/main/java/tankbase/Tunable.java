package tankbase;

public class Tunable {
    private double min;
    private double max;
    private double value;

    public Tunable(double value) {
        this(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, value);
    }

    public Tunable(double min, double max, double value) {
        this.min = min;
        this.max = max;
        setValue(value);
    }

    private double checkMinMax(double value) {
        if (value < min || value > max)
            throw new IllegalArgumentException(String.format("value must be %f < %f < %f", min, value, max));
        return value;
    }

    public double getValue() {
        return value;
    }
    public void setValue(double value) {
        this.value = checkMinMax(value);
    }
}

