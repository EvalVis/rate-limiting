package com.evalvis.database;

import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

public final class FileDbServerApplication {
    private static final Logger LOG = Logger.getLogger(FileDbServerApplication.class.getName());

    private FileDbServerApplication() {}

    public static void main(String[] args) {
        Map<String, String> env = System.getenv();
        int port = Integer.parseInt(env.getOrDefault("DB_PORT", args.length > 0 ? args[0] : "7379"));
        Path dataDir = Path.of(env.getOrDefault("DATA_DIR", args.length > 1 ? args[1] : Path.of(System.getProperty("user.home"), "filedb").toString()));

        FileDbTcpServer server = new FileDbTcpServer(port, dataDir);
        server.start();
        LOG.info("FileDb server started on port " + server.port());
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    }
}
