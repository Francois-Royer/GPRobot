/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gprobot;

import org.apache.commons.io.FileUtils;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static gprobot.RobocodeConf.*;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

/**
 * @author froyer
 */
public class RobotCodeUtil {

    static boolean fsSupportSimLink = false;

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

    static String[] compileBase = {"javac", "-cp", roboCodeJarPath};

    static String[] makeRunnerCmd(File workerDir, String opponents, String oSkills) {
        List<String> cmdList = new ArrayList();
        cmdList.add("java");
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
        cmdList.add(workerDir.toString());
        cmdList.add(opponents);
        cmdList.add(oSkills);
        String[] cmd = new String[cmdList.size()];
        return cmdList.toArray(cmd);
    }

    static String writeSource(String botName, String sourceCode) throws IOException {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(botsrcFilePath(botName)))) {
            out.write(sourceCode);
        }
        return botsrcFilePath(botName);
    }

    public static <T> T[] concatenate(T[] a, T[] b) {
        int aLen = a.length;
        int bLen = b.length;

        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);

        return c;
    }

    public static void compileBots(final String[] sources) throws Exception {
        // Compile code
        int chunkSize = sources.length / RUNNERS_COUNT;
        Thread[] compilerThread = new Thread[RUNNERS_COUNT];
        List<String> sourceList = Arrays.asList(sources);
        for (int i = 0; i < RUNNERS_COUNT; i++) {
            final int chunk = i;
            compilerThread[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    List<File> srcFile = new ArrayList<>(chunkSize);
                    for (int j = 0; j < chunkSize; j++) {
                        srcFile.add(new File(sources[chunk * chunkSize + j]));
                    }

                    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
                    Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(srcFile);
                    compiler.getTask(null, fileManager, null, null, null, compilationUnits).call();
                }
            });
            compilerThread[i].start();
        }
        for (int i = 0; i < RUNNERS_COUNT; i++) {
            compilerThread[i].join();
        }
    }

    public static void executeSync(String[] command) throws Exception {
        Process process = execute(command[0], command);
        process.waitFor();
        if (process.exitValue() != 0) {
            System.out.println(command[0] + "exited with value " + process.exitValue());
            System.exit(process.exitValue());
        }
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

        System.out.println("Deleting unused bot files");

        File oldJava, oldClass;

        for (int i = 0; i < pop; i++) {
            if (i == bestID || gen == 0 && i < 10) {
                continue;
            }
            String oldName = "X_GPbot_" + gen + "_" + i;

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
        FileUtils.copyDirectory(new File(src, dir), dest);
    }

    static void updateRunner(File runnerDir, int gen, int pop_start, int pop_end) throws IOException {
        File runnerBotsFolder = runnerDir.toPath().resolve("robots").resolve(targetPakage).toFile();
        FileUtils.cleanDirectory(runnerBotsFolder);

        for (int i = pop_start; i < pop_end; i++) {
            String name = "X_GPbot_" + gen + "_" + i;

            File srcClass = new File(botClassFilePath(name));
            File destClass = new File(runnerBotsFolder, name + ".class");
            copyOrLinkFile(srcClass.toPath(), destClass.toPath());
        }
    }

    static File[] prepareBattleRunners(int count, String opponents, String oSkills) {
        File[] battleRunnerFolders = new File[count];
        try {
            File runnerDirs = new File("battlerunners");
            if (runnerDirs.exists()) {
                FileUtils.deleteDirectory(runnerDirs);
            }
            runnerDirs.mkdir();
            for (int i = 0; i < count; i++) {
                File workerFolder = new File(runnerDirs.getAbsoluteFile(), Integer.toString(i));
                workerFolder.mkdir();
                RobotCodeUtil.copyOrLinkDir(new File(RobocodeConf.roboCodePath), workerFolder, "config");
                RobotCodeUtil.copyOrLinkDir(new File(RobocodeConf.roboCodePath), workerFolder, "libs");
                new File(workerFolder, "robots" + File.separator + "sampleex").mkdirs();
                RobotCodeUtil.copyOrLinkDir(new File(RobocodeConf.roboCodePath), workerFolder, "robots" + File.separator + "sample");
                copyOrLinkFile(new File(RobocodeConf.roboCodePath).toPath().resolve("robots").resolve("voidious.Diamond_1.8.22.jar"),
                        workerFolder.toPath().resolve("robots").resolve("voidious.Diamond_1.8.22.jar"));
                battleRunnerFolders[i] = workerFolder;
                String cmd[] = makeRunnerCmd(workerFolder, opponents, oSkills);
                execute("runner-" + i, cmd);
            }
        } catch (Exception ex) {
            Logger.getLogger(RobotCodeUtil.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
        return battleRunnerFolders;
    }

    public static void waitForFileCreated(File file) throws IOException, InterruptedException {
        Path fileDir = file.getAbsoluteFile().getParentFile().toPath();
        try (WatchService watcher = fileDir.getFileSystem().newWatchService()) {
            fileDir.register(watcher, ENTRY_CREATE);
            while (true) {
                if (file.exists()) {
                    return;
                }
                watcher.poll(1, TimeUnit.SECONDS);
            }
        }
    }
}