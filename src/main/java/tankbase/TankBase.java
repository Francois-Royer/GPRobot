package tankbase;

public class TankBase extends AbstractTankDrawingBase {

    public double avoidNan(double number, double def) {
        return Double.isNaN(number) ? def : number;
    }
}