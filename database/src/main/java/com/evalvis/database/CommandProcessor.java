package com.evalvis.database;

@FunctionalInterface
public interface CommandProcessor {
    String process(String command);
}
