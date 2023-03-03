package gprobot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static gprobot.RobocodeConf.*;

public class ExpressionNode implements Serializable {
    private static final Logger log = Logger.getLogger(ExpressionNode.class.getName());
    private static final long serialVersionUID = 8049660827041716275L;

    static final double PROB_TERM_UNIV = 0.35;
    static final double PROB_TERM_BASE = 0.65;
    static final double PROB_TERM_CONST = 0.25;
    static final double PROB_TERM_ERC = 0.1;
    static final double[] PROB_TERM = {
            PROB_TERM_CONST,
            PROB_TERM_BASE
    };
    static final double PROB_FUNC_A1 = 0.2;
    static final double PROB_FUNC_A2 = 0.6;
    static final double PROB_FUNC_A3 = 0.05;
    static final double PROB_FUNC_A4 = 0.15;
    static final double[] PROB_FUNC = {
            PROB_FUNC_A1,
            PROB_FUNC_A2,
            PROB_FUNC_A3,
            PROB_FUNC_A4
    };
    static final double PROB_MIDTREE_TERM = 0.35;
    static final double PROB_CROSS_ROOT = 0.3;
    static final double PROB_CROSS_TERMINAL = 0.1;
    static final double PROB_CROSS_FUNC = 0.6;
    static final double PROB_MUTATE_ROOT = 0.05;
    static final double PROB_MUTATE_FUNC = 0.4;
    static final double PROB_MUTATE_TERMINAL = 0.15;

    // Class Fields ///////////////////////////////////////////////////////////
    int depth;
    int arity = -1;
    boolean isTerminal;

    ExpressionNode[] child;
    String[] expression;

    // Class Methods //////////////////////////////////////////////////////////
    public ExpressionNode() {
    }

    public ExpressionNode(int depth) {
        this.depth = depth;
    }

    public ExpressionNode(int depth, int arity, boolean isTerminal) {
        this.depth = depth;
        this.arity = arity;
        this.isTerminal = isTerminal;
    }

    public String compose() {
        StringBuilder composed = new StringBuilder("(");
        composed.append(expression[0]);

        if (this.arity == -1) {
            log.severe("Arity uninitiated in ExpressionNode");
        }

        // for each child node, recursively compose its arguments
        for (int i = 0; i < this.arity; i++) {
            composed.append(child[i].compose()).append(expression[i + 1]);
        }

        composed.append(")");

        return composed.toString();
    }

    public void grow(int depth, int event) {
        setArity(depth);
        assignExpression(depth, event);
    }

    public void setArity(int depth) {
        // if depth > MIN_DEPTH this node CAN be a terminal (randomly selected)
        // if depth == MAX_DEPTH this node MUST be a terminal
        if ((depth > MIN_DEPTH && random.nextDouble() < PROB_MIDTREE_TERM) || depth == MAX_DEPTH) {
            // node will be a terminal
            this.arity = 0;
            isTerminal = true;
        } else {
            isTerminal = false;

            // pigeon-hole selection
            double pigeon = random.nextDouble();
            for (int i = 0; i < PROB_FUNC.length; i++) {
                pigeon -= PROB_FUNC[i];
                if (pigeon <= 0) {
                    this.arity = i + 1;
                    break;
                }
            }
            if (pigeon > 0) {
                log.log(Level.WARNING, "Pigeon-hole overstepped in setArity by %d", pigeon);
                this.arity = PROB_FUNC.length;
            }
            child = new ExpressionNode[this.arity];
        }
    }

    private void assignExpression(int depth, int event) {

        if (this.arity == 0) {
            assignTerminal();
        } else {
            this.expression = EXPRESSIONS[arity][random.nextInt(EXPRESSIONS[arity].length)];

            // grow the child nodes based on arity
            for (int i = 0; i < arity; i++) {
                child[i] = new ExpressionNode(depth + 1);
                child[i].grow(depth + 1, event);
            }
        }
    }

    private void assignTerminal() {
        expression = new String[1];
        this.child = null;
        double pigeon = random.nextDouble();
        for (int i = 0; i < TERMINALS.length; i++) {
            pigeon -= PROB_TERM[i];
            if (pigeon <= 0) {
                expression[0] = EXPRESSIONS[0][i][random.nextInt(EXPRESSIONS[0][i].length)];
                break;
            }
        }
        // last hole for pigeon -> ERCs
        if (pigeon > 0) {
            if (PROB_TERM_ERC < pigeon) // check that it is not here by mistake
            {
                log.log(Level.WARNING, "Pigeon-hole overstepped in assignTerminal by %d", (pigeon - PROB_TERM_ERC));
            }
            // Generate new Ephemeral Random Constant
            expression[0] = Double.toString(random.nextDouble());
        }
    }

    public void setDepths(int depth) {
        this.depth = depth;
        for (int i = 0; i < this.arity; i++) {
            this.child[i].setDepths(depth + 1);
        }
    }

    public int countNodes() {
        int count = 1;
        for (int i = 0; i < this.arity; i++) {
            count += this.child[i].countNodes();
        }
        return count;
    }

    // Genetic Methods ////////////////////////////////////////////////////////////////////
    public void insert(ExpressionNode newNode) {
        int deepity = newNode.deepestNode() - newNode.depth;    // max depth of the subtree
        int highestTerm = newNode.highestTerminal();
        int adjustedHighestTerm = highestTerm - newNode.depth;

        int floor = Math.max(1, MIN_DEPTH - deepity);    // BORDER_OFFSET selection to keep terminals below MIN_DEPTH
        int ceil = MAX_DEPTH - deepity;    // limit selection to keep terminals within MAX_DEPTH
        int targetDepth = random.nextInt(ceil - floor + 1) + floor;
        int deepestNode = this.deepestNode();

        if (targetDepth + adjustedHighestTerm < MIN_DEPTH) {    // ensure that a shallower branch in the subtree won't put a terminal under MID_DEPTH
            targetDepth = MIN_DEPTH - adjustedHighestTerm;
        }

        if (deepestNode < targetDepth) // there are no nodes deep enough, should be put on deepest node
        {
            this.insertAt(newNode, deepestNode);
        } else {    // insert at a randomly selected node in range
            this.insertAt(newNode, targetDepth);
        }
    }

    public void insertAt(ExpressionNode newNode, int target) {
        if (this.depth == target) {
            this.replaceWith(newNode);
        } else {
            ArrayList<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < this.arity; i++) {
                if (child[i].deepestNode() >= target) {
                    candidates.add(i);
                }
            }

            int targetBranch = candidates.get(random.nextInt(candidates.size()));    // randomly select a valid branch
            child[targetBranch].insertAt(newNode, target);    // go down that branch

        }
    }

    public ExpressionNode getSubTree(boolean useTerminal) {
        // if useTerminal, navigate tree and return a terminal
        if (useTerminal) {
            if (arity == 0) {
                return this.gClone();
            } else {
                return child[random.nextInt(arity)].getSubTree(true);
            }
        } else {    // get a non-root, non-terminal node
            int target = random.nextInt(this.deepestNode() - 1) + 1;
            return this.getNodeAtDepth(target);
        }
    }

    public ExpressionNode getNodeAtDepth(int target) {
        if (this.depth == target) {
            return this;
        } else {
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < this.arity; i++) {
                if (child[i].deepestNode() > target) {
                    candidates.add(i);
                }
            }
            int targetBranch = candidates.get(random.nextInt(candidates.size()));
            return child[targetBranch].getNodeAtDepth(target);
        }
    }

    public ExpressionNode gClone() {
        ExpressionNode clone = new ExpressionNode(this.depth, this.arity, this.isTerminal);
        clone.expression = new String[expression.length];

        for (int i = 0; i < expression.length; i++) {
            clone.expression[i] = this.expression[i];
        }

        if (isTerminal) {
            clone.child = null;
        } else {
            clone.child = new ExpressionNode[this.child.length];
            for (int i = 0; i < child.length; i++) {
                clone.child[i] = this.child[i].gClone();
            }
        }

        return clone;
    }

    public void replaceWith(ExpressionNode newNode) {
        this.arity = newNode.arity;
        this.isTerminal = newNode.isTerminal;

        this.expression = new String[newNode.expression.length];
        for (int i = 0; i < this.expression.length; i++) {
            this.expression[i] = newNode.expression[i];
        }

        if (newNode.isTerminal) {
            this.child = null;
        } else {    // deep-copy the rest of the tree
            this.child = new ExpressionNode[arity];
            for (int i = 0; i < newNode.arity; i++) {
                this.child[i] = new ExpressionNode(depth + 1);
                this.child[i].replaceWith(newNode.child[i]);
            }
        }
    }

    public void mutateTerminal() {
        if (!this.isTerminal) {
            this.child[random.nextInt(this.arity)].mutateTerminal();
        } else {
            this.assignTerminal();
        }
    }

    public void mutateFunction() {
        if (this.depth == 0) {
            this.child[random.nextInt(this.arity)].mutateFunction();
        } else if (this.depth == MAX_DEPTH - 1 || random.nextDouble() < 0.3) {
            ExpressionNode newSubTree = new ExpressionNode(this.depth);
            newSubTree.grow(this.depth, 0);
            this.replaceWith(newSubTree);
        }
    }

    public int deepestNode() {
        int deepest = this.depth;
        for (int i = 0; i < this.arity; i++) {
            deepest = Math.max(child[i].deepestNode(), deepest);
        }
        return deepest;
    }

    public int highestTerminal() {
        if (isTerminal) {
            return this.depth;
        } else {
            int d = MAX_DEPTH + 1;
            for (ExpressionNode exp : child) {
                d = Math.min(d, exp.highestTerminal());
            }
            return d;
        }
    }

    // Zero-Arity Expressions (terminals)
    static final String[] UNIVERSAL_TERMINALS = {
            "getEnergy()",
            "getVelocity()",
            "getX()",
            "getY()",
            "getGunHeat()",
            "getGunCoolingRate()",
            "(double) getOthers()",
            "trigoAngle(getHeadingRadians())",
            "trigoAngle(getGunHeadingRadians())",
            "trigoAngle(getRadarHeadingRadians())",
    };

    static final String[] CONSTANT_TERMINALS = {
            // Rules Constants
            "ACCELERATION",
            "DECELERATION",
            "MAX_VELOCITY",
            "MIN_BULLET_POWER",
            "MAX_BULLET_POWER",
            "MAX_TURN_RATE_RADIANS",
            "GUN_TURN_RATE_RADIANS",
            "RADAR_TURN_RATE_RADIANS",
            "ROBOT_HIT_DAMAGE",
            "ROBOT_HIT_BONUS",
            "Double.MIN_VALUE",
            "1",
            "2",
            "Math.PI",
            "Math.random()", // random value from [0, 1]
            "(Math.random()*2 - 1)", // random value from [-1, 1]
            "Math.floor(Math.random()*10)" // random int from [ 0, 10]
            //"runVar1",	// static variables
            //"runVar2"
    };

    static final String[] BASE_TERMINAL = {
            // From base
            /*"safePosition.x",
            "safePosition.y",
            "aimingData.getFiringPosition().x",
            "aimingData.getFiringPosition().y",
            "forward",
            "scandirection",
            "turnLeft",
            "turnGunLeft",
            "turnRadarLeft",
            "ahead",
            "fire",*/

            // replace scanned event
            "target.distance(getCurrentPoint())",
            "target.getVelocity()",
            "target.getDirection()",
            "target.getRotationRate()",
            "target.getAccel()",
            "target.getEnergy()",
            "target.getAngle()"
    };

    static final String[][] TERMINALS = {
            CONSTANT_TERMINALS,
            BASE_TERMINAL
    };

    static final String[][] FUNCTIONS_A1 = {
            {"Math.abs(", ")"}, // Absolute Value
            // Too much Nan caused by Arc so add %1
            {"Math.acos(", "%1)"}, // ArcCosine
            {"Math.asin(", "%1)"}, // ArcSine
            {"Math.cos(", ")"}, // Cosine
            {"Math.sin(", ")"}, // Sine
            {"Math.sqrt(Math.abs(", "))"}, // square root
            {"Math.exp(", ")"}, // e^x
            {"Math.log(Math.abs(", "))"},
//            {"Rules.getBulletDamage(", ")"},
//            {"Rules.getBulletSpeed(", ")"},
//            {"Rules.getGunHeat(", ")"},
//            {"trigoAngle(", ")"},
//            {"oppositeAngle(", ")"},
//            {"normalRelativeAngle(", ")"}
    };

    static final String[][] FUNCTIONS_A2 = {
            {"", " - ", ""}, // add
            {"", " + ", ""}, // subtract
            {"", " * ", ""}, // multiply
            {"", " / ", ""}, // divide (CHECK FOR ZERO!)
            {"Math.min(", ", ", ")"}, // minimum
            {"Math.max(", ", ", ")"}, // maximum
    };

    static final String[][] FUNCTIONS_A3 = {
            {"", " > 0 ? ", " : ", ""}, // X > 0 ? ifYes : ifNo
    };

    static final String[][] FUNCTIONS_A4 = {
            {"", " > ", " ? ", " : ", ""}, // X > Y ? ifYes : ifNo
            {"", " != ", " ? ", " : ", ""} // X != Y ? ifYes : ifNo
    };

    // All expressions available to the GP
    static final String[][][] EXPRESSIONS = {
            TERMINALS,
            FUNCTIONS_A1,
            FUNCTIONS_A2,
            FUNCTIONS_A3,
            FUNCTIONS_A4
    };
}
