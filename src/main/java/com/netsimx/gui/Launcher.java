package com.netsimx.gui;

/**
 * Entry point for the packaged fat jar (see pom.xml's shade-plugin config).
 *
 * This class deliberately does NOT extend {@link javafx.application.Application}
 * itself. The JVM launcher performs a special check when the declared
 * Main-Class of a jar directly extends Application and JavaFX isn't on the
 * module path (which it isn't, once shaded flat into a single jar) - that
 * check fails with "JavaFX runtime components are missing, and are required
 * to run this application" even though the classes are right there on the
 * classpath. Routing through this indirection class avoids that check
 * entirely; {@link NetSimXApp} itself is unchanged and works exactly the
 * same whether launched this way or via {@code mvn javafx:run}.
 */
public class Launcher {
    public static void main(String[] args) {
        NetSimXApp.main(args);
    }
}
