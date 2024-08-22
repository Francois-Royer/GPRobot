package gprobot;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static gprobot.RobocodeConf.*;
import static gprobot.RobotCodeUtil.*;

public class BattleControler {

    enum Status {Starting, Ready, Running}

    private final Logger log;
    private Process battleRunner;
    private PrintStream stdin;
    private final int controlerId;
    private File workerFolder;
    private Status status;

    double fitness;

    public BattleControler(int controlerId) {
        this.controlerId = controlerId;
        log = Logger.getLogger(BattleControler.class.getName() + controlerId);
        createRobocodeRuntimeFolder();
        startBattleRunner();
    }

    private void createRobocodeRuntimeFolder() {
        try {
            workerFolder = new File(getRunnersDir().getAbsoluteFile(), Integer.toString(controlerId));
            workerFolder.mkdir();
            copyOrLinkDir(new File(RobocodeConf.ROBO_CODE_PATH), workerFolder, "config");
            copyOrLinkDir(new File(RobocodeConf.ROBO_CODE_PATH), workerFolder, "libs");

            new File(workerFolder, ROBOTS_FOLDER + File.separator).mkdirs();
            copyOrLinkDir(new File(RobocodeConf.ROBO_CODE_PATH), workerFolder, ROBOTS_FOLDER + File.separator + "sample");
            copyOrLinkDir(new File(RobocodeConf.ROBO_CODE_PATH), workerFolder, ROBOTS_FOLDER + File.separator + "tankbase");
            copyOrLinkFile(new File(RobocodeConf.ROBO_CODE_PATH + File.separator + ROBOTS_FOLDER + File.separator + "voidious.Diamond_1.8.28.jar").toPath(),
                    new File(workerFolder, ROBOTS_FOLDER + File.separator + "voidious.Diamond_1.8.28.jar").toPath());
        } catch (Exception ex) {
            log.log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }

    private String[] makeRunnerCmd() {
        List<String> cmdList = new ArrayList();
        cmdList.add("java");
        cmdList.add("-Xmx512m");
        cmdList.add("-cp");
        cmdList.add("libs/*");
        cmdList.add("-Djava.awt.headless=true");
        if (Runtime.version().feature() > 11)
            cmdList.add("-Djava.security.manager=allow");

        cmdList.add("--add-opens=java.base/sun.net.www.protocol.jar=ALL-UNNAMED");
        cmdList.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
        cmdList.add("--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED");
        cmdList.add("--add-opens=java.desktop/sun.awt=ALL-UNNAMED");

        if (Runtime.version().feature() > 18)
            cmdList.add("-Xshare:off");

        // To Debug remotly
        //cmdList.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");

        cmdList.add("gprobot.BattleRunner");
        cmdList.add(Integer.toString(controlerId));
        cmdList.add(workerFolder.toString());
        return cmdList.toArray(String[]::new);
    }

    private void startBattleRunner() {
        try {
            battleRunner = Runtime.getRuntime().exec(makeRunnerCmd(), new String[0], workerFolder);
            stdin = new PrintStream(battleRunner.getOutputStream(), true);
            pipeStream(battleRunner.getInputStream(), System.out);
            pipeStream(battleRunner.getErrorStream(), System.err);
            status = Status.Starting;
        } catch (Exception ex) {
            log.log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }

    private void pipeStream(final InputStream is, final PrintStream os) {
        Logger log = this.log;
        new Thread(() -> {
            String line = null;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(is))) {
                while ((line = in.readLine()) != null) {
                    if (line.startsWith(MSG))
                        handleRunnerMessage(line.substring(MSG.length() + 1));
                    else if (!line.startsWith("Load") && !line.startsWith("WARNING:")) // Filter RobotCode engine loading message
                        os.printf("%03d | %s%n", controlerId, line);
                }
            } catch (IOException ioe) {
                log.log(Level.SEVERE, "printMsg", ioe);
            } catch (InterruptedException ie) {
                log.log(Level.SEVERE, "printMsg", ie);
            }
        }).start();
    }

    private void handleRunnerMessage(String message) throws InterruptedException {
        if (!message.startsWith(READY))
            fitness = Double.parseDouble(message);
        setWaiting();
    }

    private void killBattleRunner() {
        if (battleRunner != null) {
            battleRunner.destroy();
            battleRunner = null;
        }
    }

    public long waitReadyStatus() throws InterruptedException {
        synchronized (workerFolder) {
            long begin = System.currentTimeMillis();
            while (status != Status.Ready) workerFolder.wait();
            return System.currentTimeMillis() - begin;
        }
    }

    void setWaiting() throws InterruptedException {
        synchronized (workerFolder) {
            status = Status.Ready;
            workerFolder.notifyAll();
        }
    }

    void setRunning() throws InterruptedException {
        synchronized (workerFolder) {
            while (status != Status.Ready) workerFolder.wait();
            status = Status.Running;
        }
    }

    public void setOpponentsName(String[] names) throws InterruptedException, IOException {
        waitReadyStatus();
        stdin.println(SET_OPPONENTS + " " + String.join(",", names));
    }

    public double getRobotFitness(String robot) throws InterruptedException, IOException {
        setRunning();
        stdin.println(GET_FITNESS + " " + robot);
        long duration = waitReadyStatus();
        log.fine(robot + " fitness is " + fitness + ", batle duration " + duration);
        return fitness;
    }

    public void destroy() {
        battleRunner.destroy();
    }

}

