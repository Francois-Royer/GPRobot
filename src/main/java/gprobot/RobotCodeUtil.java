/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gprobot;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.net.InetAddress;
import java.nio.file.Path;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static gprobot.RobocodeConf.*;
import static java.nio.file.Files.*;

/**
 * @author froyer
 */
public class RobotCodeUtil {
    private static Logger log = Logger.getLogger(RobotCodeUtil.class.getName());

    private RobotCodeUtil() {
        // Util class
    }

    static boolean fsSupportSimLink = false;
    static {
        try {
            File symLink = new File("./symLinkTest" + UUID.randomUUID());
            createSymbolicLink(symLink.toPath(), new File(".").toPath());
            symLink.delete();

            fsSupportSimLink = true;// && !System.getProperty("os.name").toLowerCase().contains("win");
            log.log(Level.FINE, "Local file system support symbolic link");
        } catch (Exception e) {
            log.log(Level.FINE, "Local file system don't support symbolic link");
            // fs may not support symlink, too bad...
        }
    }


    static String[] makeRunnerCmd(File workerDir, int number) {
        List<String> cmdList = new ArrayList();
        cmdList.add("java");
        cmdList.add("-Djava.awt.headless=true");
        cmdList.add("-DNOSECURITY=true"); // RMI cause security exception
        cmdList.add("-Xmx512m");

        cmdList.add("-cp");

        StringBuilder cpBuilder = new StringBuilder(workerDir.toPath().resolve("libs").resolve(ROBOCODE_JAR).toString());
        String[] currentClassPath = System.getProperty("java.class.path").split(File.pathSeparator);
        Stream.of(currentClassPath)
                .filter(p -> !p.contains(ROBOCODE_JAR))
                .forEach(p -> cpBuilder.append(File.pathSeparator).append(p));
        cmdList.add(cpBuilder.toString());

        cmdList.add("gprobot.BattleRunner");
        cmdList.add(Integer.toString(number));
        cmdList.add(workerDir.toString());

        String[] cmd = new String[cmdList.size()];
        return cmdList.toArray(cmd);
    }

    public static void compileBots(final String[] sources) throws InterruptedException {
        // Compile code
        ExecutorService executorService = new ThreadPoolExecutor(AVAILABLE_PROCESSORS, AVAILABLE_PROCESSORS, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        List<List<String>> chunks = chunkList(Arrays.asList(sources), 20);

        for (List<String> chunk : chunks) {
            final List<String> srcs = chunk;
            executorService.submit(() -> {
                try {
                    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
                    Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(srcs);
                    List<String> options = new ArrayList<>();
                    options.addAll(Arrays.asList("-classpath",System.getProperty("java.class.path")));
                    JavaCompiler.CompilationTask t = compiler.getTask(null, fileManager, null, options, null, compilationUnits);
                    t.call();
                } catch (Exception e) {
                    log.log(Level.SEVERE,"CompileBots", e);
                }
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.MINUTES);
    }

    public static List<List<String>> chunkList(List<String> source, int chunksize) {
        List<List<String>> result = new ArrayList<>();
        int start = 0;
        while (start < source.size()) {
            int end = Math.min(source.size(), start + chunksize);
            result.add(source.subList(start, end));
            start += chunksize;
        }
        return result;
    }

    public static Process execute(String name, String[] command) throws IOException {
        log.log(Level.FINE, "execute " + name + ": " + String.join(" ", command));
        Process process = Runtime.getRuntime().exec(command);
        printMsg(name + " |", process.getInputStream());
        printMsg(name + " |", process.getErrorStream());
        return process;
    }

    private static void printMsg(final String name, final InputStream ins) {
        new Thread(() -> {
            Logger logger = Logger.getLogger(name);
            String line = null;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(ins))) {
                while ((line = in.readLine()) != null) {

                    if (!line.startsWith("Load")) { // Filter RobotCode engine loading message
                        logger.info(line);
                    }
                }
            } catch (IOException ioe) {
                log.log(Level.SEVERE,"printMsg", ioe);
            }
        }).start();
    }

    public static void clearBots(int gen, int pop, int bestID) {
        File toDelete;
        File oldClass;

        for (int i = 0; i < pop; i++) {
            if (i == bestID || gen == 0 && i < 10) {
                continue;
            }
            String oldName = getRobotName(gen, i);

            new File(botsrcFilePath(oldName)).delete();
            new File(botClassFilePath(oldName)).delete();
            new File(botOpponentClassFilePath(oldName)).delete();
        }
    }

    public static final String botsrcFilePath(String botName) {
        return RobocodeConf.TARGET_FOLDER + File.separator + botName + ".java";
    }

    public static final String botClassFilePath(String botName) {
        return RobocodeConf.TARGET_FOLDER + File.separator + botName + ".class";
    }

    public static final String botOpponentClassFilePath(String botName) {
        return RobocodeConf.TARGET_FOLDER + File.separator + botName + "$Opponent.class";
    }

    public static void copyOrLinkFile(Path src, Path target) throws IOException {
        log.log(Level.FINE, "copyOrLink " + src + " " + target);
        if (fsSupportSimLink) {
            createSymbolicLink(target, src);
        } else {
            copy(src, target);
        }
    }

    public static void copyOrLinkDir(File src, File target, String dir) throws IOException {
        if (fsSupportSimLink) {
            File ltarget = new File(target, dir);
            File link = new File(src, dir);
            createSymbolicLink(ltarget.toPath(), link.toPath());
        } else {
            copyDir(src, target, dir);
        }
    }

    public static void copyDir(File src, File target, String dir) {
        File dest = new File(target, dir);
        dest.mkdirs();
        copyFolder(new File(src, dir).toPath(), dest.toPath());
    }

    static void updateRunner(File runnerDir, String className) throws IOException {
        File runnerBotsFolder = runnerDir.toPath().resolve(ROBOTS_FOLDER).resolve(TARGET_PACKAGE).toFile();
        cleanDirectory(runnerBotsFolder);

        addClassRunner(runnerDir, className);
        if (className != "GPBase")
            addClassRunner(runnerDir, "GPBase");
        addClassRunner(runnerDir, "GPBase$Enemy");
    }

    static void addClassRunner(File runnerDir, String className) throws IOException {
        File runnerBotsFolder = runnerDir.toPath().resolve(ROBOTS_FOLDER).resolve(TARGET_PACKAGE).toFile();
        File srcClass = new File(botClassFilePath(className));
        File destClass = new File(runnerBotsFolder, className + ".class");
        copyOrLinkFile(srcClass.toPath(), destClass.toPath());
    }

    public static File getRunnersDir() {
        return new File("battlerunners");
    }

    public static File getRunnerDir(int runnerId) {
        return new File(getRunnersDir().getAbsoluteFile(), Integer.toString(runnerId));
    }

    static Process[] runnerProcess;

    public static void prepareBattleRunners(int count) {
        try {
            File runnerDirs = getRunnersDir();
            if (runnerDirs.exists()) {
                delete(runnerDirs);
            }
            runnerDirs.mkdir();
            runnerProcess = new Process[count];
            for (int i = 0; i < count; i++) {
                File workerFolder = getRunnerDir(i);
                workerFolder.mkdir();
                copyOrLinkDir(new File(RobocodeConf.ROBO_CODE_PATH), workerFolder, "config");
                copyOrLinkDir(new File(RobocodeConf.ROBO_CODE_PATH), workerFolder, "libs");
                new File(workerFolder, ROBOTS_FOLDER+ File.separator + "sampleex").mkdirs();
                copyOrLinkDir(new File(RobocodeConf.ROBO_CODE_PATH), workerFolder, ROBOTS_FOLDER + File.separator + "sample");
                String[] cmd = makeRunnerCmd(workerFolder, i);
                runnerProcess[i] = execute("runner-" + i, cmd);
            }
        } catch (Exception ex) {
            log.log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }

    static void restartProcess(int i) {
        try {
            runnerProcess[i].destroyForcibly();
            runnerProcess[i].waitFor();
            File workerFolder = getRunnerDir(i);
            String[] cmd = makeRunnerCmd(workerFolder, i);
            runnerProcess[i] = execute("runner-" + i, cmd);
        } catch (Exception e) {
            log.log(Level.SEVERE, "restartProcess " +  i, e);
            System.exit(1);
        }
    }

    public static String sDuration(long duration) {
        return Duration.ofMillis(duration).toString().substring(2);
    }

    public static String getRobotName(int gen, int id) {
        return String.format("X_GPbot_%d_%d", gen, id);
    }

    public static String getRunnerUrl(String runnerAddr, int runnerId) {
        return String.format("rmi://%s/GRunner%d", runnerAddr, runnerId);
    }

    public static void cleanDirectory(File dir) {
        if (dir.isDirectory()) {
            Stream.of(dir.listFiles()).forEach(File::delete);
        }
    }

    public static void delete(File f) throws IOException {
        walk(f.toPath())
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach(File::delete);

    }

    public static void copyFolder(Path src, Path dest) {
        try {
            walk(src).forEach(s -> copyFolderEntry(src, dest, s));
        } catch (Exception ex) {
            log.log(Level.SEVERE, "copyFolder", ex);
        }
    }

    private static void copyFolderEntry(Path src, Path dest, Path s) {
        try {
            Path d = dest.resolve(src.relativize(s));
            if (s.toFile().isDirectory()) {
                if (!d.toFile().exists())
                    createDirectory(d);
                return;
            }
            copy(s, d);// use flag to override existing
        } catch (Exception e) {
            log.log(Level.SEVERE, "copyFolder", e);
        }
    }

    public static void killallRunner() {
        Stream.of(runnerProcess).forEach(Process::destroy);
        try {
            delete(getRunnersDir());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}