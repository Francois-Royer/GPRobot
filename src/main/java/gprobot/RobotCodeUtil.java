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
import java.nio.file.Files;
import java.nio.file.Path;
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

import static gprobot.RobocodeConf.roboCodeJar;
import static gprobot.RobocodeConf.targetPakage;

/**
 * @author froyer
 */
public class RobotCodeUtil {

    static boolean fsSupportSimLink = false;
    static int nbProcs = Runtime.getRuntime().availableProcessors();

    static {
        try {
            File symLink = new File("./symLinkTest" + UUID.randomUUID());
            Files.createSymbolicLink(symLink.toPath(), new File(".").toPath());
            symLink.delete();

            fsSupportSimLink = true;
        } catch (Exception e) {
            // fs may not support symlink, too bad...
        }
    }

    static String[] makeRunnerCmd(File workerDir, int number) {
        List<String> cmdList = new ArrayList();
        cmdList.add("java");
        cmdList.add("-DNOSECURITY=true"); // RMI cause security exception
        cmdList.add("-cp");
        StringBuilder cpBuilder = new StringBuilder(workerDir.toPath().resolve("libs").resolve(roboCodeJar).toString());

        String[] currentClassPath = System.getProperty("java.class.path").split(File.pathSeparator);

        for (String path : currentClassPath) {
            if (!path.contains(roboCodeJar)) {
                cpBuilder.append(File.pathSeparator).append(path);
            }
        }
        cmdList.add(cpBuilder.toString());
        cmdList.add("gprobot.BattleRunner");
        cmdList.add(Integer.toString(number));
        cmdList.add(workerDir.toString());
        String[] cmd = new String[cmdList.size()];
        return cmdList.toArray(cmd);
    }

    public static void compileBots(final String[] sources) throws Exception {
        // Compile code
        ExecutorService executorService = new ThreadPoolExecutor(nbProcs, nbProcs, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        List<List<String>> chunks = chunkList(Arrays.asList(sources), 20);

        for (List<String> chunk : chunks) {
            final List<String> srcs = chunk;
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
                        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromStrings(srcs);
                        compiler.getTask(null, fileManager, null, null, null, compilationUnits).call();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.MINUTES);
    }

    public static List<List<String>> chunkList(List<String> source, int chunksize) {
        List<List<String>> result = new ArrayList<List<String>>();
        int start = 0;
        while (start < source.size()) {
            int end = Math.min(source.size(), start + chunksize);
            result.add(source.subList(start, end));
            start += chunksize;
        }
        return result;
    }

    public static Process execute(String name, String[] command) throws Exception {
        Process process = Runtime.getRuntime().exec(command);
        printMsg(name + " |", process.getInputStream());
        printMsg(name + " |", process.getErrorStream());
        return process;
    }

    private static void printMsg(final String name, final InputStream ins) throws Exception {
        new Thread(new Runnable() {
            public void run() {
                String line = null;
                try (BufferedReader in = new BufferedReader(new InputStreamReader(ins))) {
                    while ((line = in.readLine()) != null) {

                        if (!line.startsWith("Load")) { // Filter RobotCode engine loading message
                            System.out.println(name + " " + line);
                        }
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }).start();
    }

    public static void clearBots(int gen, int pop, int bestID) {
        File oldJava, oldClass;

        for (int i = 0; i < pop; i++) {
            if (i == bestID || gen == 0 && i < 10) {
                continue;
            }
            String oldName = gRobotName(gen, i);

            oldJava = new File(botsrcFilePath(oldName));
            oldClass = new File(botClassFilePath(oldName));
            oldJava.delete();
            oldClass.delete();
        }
    }

    public static final String botsrcFilePath(String botName) {
        return RobocodeConf.targetFolder + File.separator + botName + ".java";
    }

    public static final String botClassFilePath(String botName) {
        return RobocodeConf.targetFolder + File.separator + botName + ".class";
    }

    public static void copyOrLinkFile(Path src, Path target) throws IOException {
        if (fsSupportSimLink) {
            Files.createSymbolicLink(target, src);
        } else {
            Files.copy(src, target);
        }
    }

    public static void copyOrLinkDir(File src, File target, String dir) throws IOException {
        if (fsSupportSimLink) {
            File ltarget = new File(target, dir);
            File link = new File(src, dir);
            Files.createSymbolicLink(ltarget.toPath(), link.toPath());
        } else {
            copyDir(src, target, dir);
        }
    }

    public static void copyDir(File src, File target, String dir) throws IOException {
        File dest = new File(target, dir);
        dest.mkdirs();
        copyFolder(new File(src, dir).toPath(), dest.toPath());
    }

    static void updateRunner(File runnerDir, int gen, int robotid) throws IOException {
        File runnerBotsFolder = runnerDir.toPath().resolve("robots").resolve(targetPakage).toFile();
        cleanDirectory(runnerBotsFolder);
        String name = gRobotName(gen, robotid);

        File srcClass = new File(botClassFilePath(name));
        File destClass = new File(runnerBotsFolder, name + ".class");
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
                copyOrLinkDir(new File(RobocodeConf.roboCodePath), workerFolder, "config");
                copyOrLinkDir(new File(RobocodeConf.roboCodePath), workerFolder, "libs");
                new File(workerFolder, "robots" + File.separator + "sampleex").mkdirs();
                copyOrLinkDir(new File(RobocodeConf.roboCodePath), workerFolder, "robots" + File.separator + "sample");
                copyOrLinkFile(new File(RobocodeConf.roboCodePath).toPath().resolve("robots").resolve("voidious.Diamond_1.8.22.jar"),
                    workerFolder.toPath().resolve("robots").resolve("voidious.Diamond_1.8.22.jar"));
                String cmd[] = makeRunnerCmd(workerFolder, i);
                runnerProcess[i] = execute("runner-" + i, cmd);
            }
        } catch (Exception ex) {
            Logger.getLogger(RobotCodeUtil.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }

    static void restartProcess(int i) {
        try {
            runnerProcess[i].destroyForcibly();
            runnerProcess[i].waitFor();
            File workerFolder = getRunnerDir(i);
            String cmd[] = makeRunnerCmd(workerFolder, i);
            runnerProcess[i] = execute("runner-" + i, cmd);
        } catch (Exception e) {
            Logger.getLogger(RobotCodeUtil.class.getName()).log(Level.SEVERE, null, e);
            System.exit(1);
        }
    }

    public static String sDuration(long duration) {
        return Duration.ofMillis(duration).toString().substring(2);
    }

    public static String gRobotName(int gen, int id) {
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
        Files.walk(f.toPath())
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach(File::delete);

    }

    public static void copyFolder(Path src, Path dest) {
        try {
            Files.walk(src)
                .forEach(s ->
                {
                    try {
                        Path d = dest.resolve(src.relativize(s));
                        if (Files.isDirectory(s)) {
                            if (!Files.exists(d))
                                Files.createDirectory(d);
                            return;
                        }
                        Files.copy(s, d);// use flag to override existing
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void killallRunner() {
        for (int i = 0; i < runnerProcess.length; i++) {
            runnerProcess[i].destroyForcibly();
        }
    }
}