import java.util.concurrent.*;

class MockConnection {
    private String name;

    public MockConnection(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

class ConnectionPool {
    // Double-checked locking for lazy initialization
    private static volatile ConnectionPool instance;
    private BlockingQueue<MockConnection> pool;
    private final int poolSize = 5;

    private ConnectionPool() {
        pool = new LinkedBlockingQueue<>(poolSize);
        for (int i = 1; i <= poolSize; i++) {
            pool.offer(new MockConnection("Connection " + i));
        }
    }

    public static ConnectionPool getInstance() {
        if (instance == null) {
            synchronized (ConnectionPool.class) {
                if (instance == null) {
                    instance = new ConnectionPool();
                }
            }
        }
        return instance;
    }

    public MockConnection getConnection() throws InterruptedException {
        // waits if no connection is available
        return pool.take();
    }

    public void releaseConnection(MockConnection connection) {
        pool.offer(connection);
    }
}

class ConnectionTask implements Runnable {
    private final ConnectionPool connectionPool;

    public ConnectionTask(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    @Override
    public void run() {
        try {
            MockConnection connection = connectionPool.getConnection();
            System.out.println(Thread.currentThread().getName() + " acquired " + connection.getName());
            // Simulate some work with the connection
            Thread.sleep(2000);
            connectionPool.releaseConnection(connection);
            System.out.println(Thread.currentThread().getName() + " released " + connection.getName());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class CompletableConnectionTask implements Runnable {
    private final ConnectionPool connectionPool;

    public CompletableConnectionTask(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public Future<Void> runTask() {
        return CompletableFuture.runAsync(() -> {
            try {
                MockConnection connection = connectionPool.getConnection();
                System.out.println(Thread.currentThread().getName() + " acquired " + connection.getName());

                // simulate work with connection
                Thread.sleep(2000);
                connectionPool.releaseConnection(connection);
                System.out.println(Thread.currentThread().getName() + " released " + connection.getName());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).exceptionally(ex -> {
            System.err.println("Exception: " + ex.getMessage());
            return null;
        });
    }

    @Override
    public void run() {
        runTask();
    }
}

public class Main {

    public static void main(String[] args) {
        ConnectionPool connectionPool = ConnectionPool.getInstance();
        ExecutorService threadPool = Executors.newFixedThreadPool(7);

        System.out.println("Running with basic Threads using Runnable and Thread...");

        // Example with regular Threads (Runnable)
        for (int i = 0; i < 7; i++) {
            // Using Runnable
            threadPool.execute(new ConnectionTask(connectionPool));
        }

        // Wait for all threads to finish before proceeding
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
        System.out.println("\nRunning with CompletableFuture and IFuture...");

        // re-initialize the thread pool
        ExecutorService completableFutureThreadPool = Executors.newFixedThreadPool(7);

        // example with CompletableFuture and IFuture
        for (int i = 0; i < 7; i++) {
            new CompletableConnectionTask(connectionPool).runTask();
        }

        completableFutureThreadPool.shutdown();
        try {
            if (!completableFutureThreadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                completableFutureThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            completableFutureThreadPool.shutdownNow();
        }
    }
}
