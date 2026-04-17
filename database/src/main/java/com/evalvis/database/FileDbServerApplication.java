package com.evalvis.database;

import java.nio.file.Path;

public final class FileDbServerApplication {
    private FileDbServerApplication() {
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 7379;
        Path rootDirectory = args.length > 1 ? Path.of(args[1]) : Path.of(System.getProperty("user.home"), "filedb");
        FileDbTcpServer server = new FileDbTcpServer(port, rootDirectory);
        server.start();
    }
}
