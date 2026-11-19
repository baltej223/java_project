package com.example.chroner;

import com.example.chroner.call.HttpCaller;
import com.example.chroner.cli.Cli;
import com.example.chroner.config.Config;
import com.example.chroner.db.Database;
import com.example.chroner.db.HistoryStore;
import com.example.chroner.db.JobStore;
import com.example.chroner.db.SecretsCipher;
import com.example.chroner.db.TaskStore;
import com.example.chroner.db.UserStore;
import com.example.chroner.http.AuthApi;
import com.example.chroner.http.EchoApi;
import com.example.chroner.http.JobApi;
import com.example.chroner.http.Router;
import com.example.chroner.http.TaskApi;
import com.example.chroner.http.WebUi;
import com.example.chroner.services.ArchiverLoop;
import com.example.chroner.services.AuthService;
import com.example.chroner.services.ExecutorLoop;
import com.example.chroner.services.JobService;
import com.example.chroner.services.ReconcilerLoop;
import com.example.chroner.services.SchedulerLoop;
import com.example.chroner.services.TaskService;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One binary with a role per argument. "all" is the classroom default. */
public final class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() {
    }

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$s %3$s: %5$s%6$s%n");
        String mode = args.length == 0 ? "all" : args[0];
        try {
            if ("cli".equals(mode)) {
                String[] rest = new String[Math.max(0, args.length - 1)];
                System.arraycopy(args, 1, rest, 0, rest.length);
                Cli.run(rest);
                return;
            }
            run(mode);
        } catch (Exception ex) {
            System.err.println("error: " + ex.getMessage());
            if (!(ex instanceof IllegalArgumentException)
                    && !(ex instanceof IllegalStateException)) {
                LOG.log(Level.SEVERE, "startup failed", ex);
            }
            System.exit(1);
        }
    }

    private static void run(String mode) throws Exception {
        Config config = new Config();
        if ("echo".equals(mode)) {
            runEcho(config);
            return;
        }
        if ("task-api".equals(mode)) {
            runTaskApi(config);
            return;
        }
        if ("job-api".equals(mode)) {
            runJobApi(config);
            return;
        }
        if ("scheduler".equals(mode)) {
            runScheduler(config);
            return;
        }
        if ("executor".equals(mode)) {
            runExecutor(config);
            return;
        }
        if ("reconciler".equals(mode)) {
            runReconciler(config);
            return;
        }
        if ("archiver".equals(mode)) {
            runArchiver(config);
            return;
        }
        if ("all".equals(mode)) {
            runAll(config);
            return;
        }
        throw new IllegalArgumentException("unknown mode: " + mode);
    }

    private static Database database(Config config) {
        Database database = new Database(config);
        database.ping();
        return database;
    }

    private static SecretsCipher cipher(Config config) {
        return new SecretsCipher(config.requireSecretsKey());
    }

    private static void runEcho(Config config) throws Exception {
        HttpServer server = startServer(config.getEchoPort(), new EchoApi(), 8);
        awaitShutdown(server, null, new ArrayList<>());
    }

    private static void runTaskApi(Config config) throws Exception {
        String jwt = config.requireJwtSecret();
        Database database = database(config);
        TaskStore store = new TaskStore(database, cipher(config));
        HttpServer server = startServer(config.getTaskApiPort(),
                TaskApi.router(new TaskService(store), jwt), 16);
        awaitShutdown(server, null, new ArrayList<>());
    }

    private static void runJobApi(Config config) throws Exception {
        String jwt = config.requireJwtSecret();
        Database database = database(config);
        SecretsCipher cipher = cipher(config);
        JobStore jobs = new JobStore(database, cipher);
        HistoryStore history = new HistoryStore(database);
        HttpServer server = startServer(config.getJobApiPort(),
                JobApi.router(new JobService(jobs, history), jwt), 16);
        awaitShutdown(server, null, new ArrayList<>());
    }

    private static void runScheduler(Config config) throws Exception {
        Database database = database(config);
        TaskStore tasks = new TaskStore(database, cipher(config));
        List<Thread> threads = new ArrayList<>();
        startLoop(threads, "chroner-scheduler", new SchedulerLoop(
                tasks, config.getSchedulerIntervalMs(),
                config.getSchedulerLookaheadMs(), config.getSchedulerBatch()));
        awaitShutdown(null, null, threads);
    }

    private static void runExecutor(Config config) throws Exception {
        Database database = database(config);
        JobStore jobs = new JobStore(database, cipher(config));
        HttpCaller caller = new HttpCaller(config.getExecutorAllowCidrs());
        ExecutorLoop loop = new ExecutorLoop(jobs, caller, config.getWorkerId(),
                config.getExecutorWorkers(), config.getExecutorPollMs());
        List<Thread> threads = new ArrayList<>();
        startLoop(threads, "chroner-executor", loop);
        awaitShutdown(null, loop, threads);
    }

    private static void runReconciler(Config config) throws Exception {
        Database database = database(config);
        JobStore jobs = new JobStore(database, cipher(config));
        List<Thread> threads = new ArrayList<>();
        startLoop(threads, "chroner-reconciler", new ReconcilerLoop(
                jobs, config.getReconcilerIntervalMs(), config.getReconcilerBatch()));
        awaitShutdown(null, null, threads);
    }

    private static void runArchiver(Config config) throws Exception {
        Database database = database(config);
        SecretsCipher cipher = cipher(config);
        JobStore jobs = new JobStore(database, cipher);
        HistoryStore history = new HistoryStore(database);
        List<Thread> threads = new ArrayList<>();
        startLoop(threads, "chroner-archiver", new ArchiverLoop(
                jobs, history, config.getArchiverIntervalMs(), config.getArchiverBatch(),
                config.getArchiveSucceededAfterMs(), config.getArchiveDeadAfterMs(),
                config.getHistoryTtlMs()));
        awaitShutdown(null, null, threads);
    }

    private static void runAll(Config config) throws Exception {
        String jwt = config.requireJwtSecret();
        Database database = database(config);
        SecretsCipher cipher = cipher(config);
        TaskStore tasks = new TaskStore(database, cipher);
        JobStore jobs = new JobStore(database, cipher);
        HistoryStore history = new HistoryStore(database);
        UserStore users = new UserStore(database);
        AuthService authService = new AuthService(
                users, jwt, config.getAuthTokenTtlSeconds());
        TaskService taskService = new TaskService(tasks);
        JobService jobService = new JobService(jobs, history);
        HttpCaller caller = new HttpCaller(config.getExecutorAllowCidrs());

        ExecutorService taskPool = Executors.newFixedThreadPool(16, daemonFactory("task-api"));
        ExecutorService jobPool = Executors.newFixedThreadPool(16, daemonFactory("job-api"));
        // The dashboard and its API share one origin on the task-api port.
        Router combined = TaskApi.router(taskService, jwt);
        JobApi.register(combined, jobService);
        AuthApi.register(combined, authService);
        HttpServer taskServer = startServer(config.getTaskApiPort(),
                new WebUi(combined, Path.of(config.getWebRoot())), taskPool);
        HttpServer jobServer = startServer(config.getJobApiPort(),
                JobApi.router(jobService, jwt), jobPool);
        LOG.info("web root=" + config.getWebRoot());

        List<Thread> threads = new ArrayList<>();
        startLoop(threads, "chroner-scheduler", new SchedulerLoop(
                tasks, config.getSchedulerIntervalMs(),
                config.getSchedulerLookaheadMs(), config.getSchedulerBatch()));
        ExecutorLoop executor = new ExecutorLoop(jobs, caller, config.getWorkerId(),
                config.getExecutorWorkers(), config.getExecutorPollMs());
        startLoop(threads, "chroner-executor", executor);
        startLoop(threads, "chroner-reconciler", new ReconcilerLoop(
                jobs, config.getReconcilerIntervalMs(), config.getReconcilerBatch()));
        startLoop(threads, "chroner-archiver", new ArchiverLoop(
                jobs, history, config.getArchiverIntervalMs(), config.getArchiverBatch(),
                config.getArchiveSucceededAfterMs(), config.getArchiveDeadAfterMs(),
                config.getHistoryTtlMs()));

        awaitShutdown(taskServer, jobServer, executor, threads, taskPool, jobPool);
    }

    private static HttpServer startServer(int port, HttpHandler handler, int threads)
            throws Exception {
        return startServer(port, handler,
                Executors.newFixedThreadPool(threads, daemonFactory("http-" + port)));
    }

    private static HttpServer startServer(int port, HttpHandler handler,
                                          ExecutorService pool) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", handler);
        server.setExecutor(pool);
        server.start();
        LOG.info("listening port=" + port);
        return server;
    }

    private static java.util.concurrent.ThreadFactory daemonFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("chroner-" + prefix);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void startLoop(List<Thread> threads, String name, Runnable loop) {
        Thread thread = new Thread(loop);
        thread.setName(name);
        thread.start();
        threads.add(thread);
    }

    private static void awaitShutdown(HttpServer server, ExecutorLoop executor,
                                      List<Thread> threads) throws InterruptedException {
        awaitShutdown(server, null, executor, threads, null, null);
    }

    private static void awaitShutdown(HttpServer first, HttpServer second,
                                      ExecutorLoop executor, List<Thread> threads,
                                      ExecutorService firstPool, ExecutorService secondPool)
            throws InterruptedException {
        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("stopping");
            if (first != null) {
                first.stop(10);
            }
            if (second != null) {
                second.stop(10);
            }
            if (executor != null) {
                executor.stop();
            }
            for (Thread thread : threads) {
                thread.interrupt();
            }
            if (firstPool != null) {
                firstPool.shutdownNow();
            }
            if (secondPool != null) {
                secondPool.shutdownNow();
            }
            stop.countDown();
        }, "chroner-shutdown"));
        stop.await();
    }
}
