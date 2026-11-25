package cn.edu.oslab.desktop;

/** Separate launcher allows JavaFX to run from the packaged classpath. */
public final class DesktopLauncher {
    public static void main(String[] args) {
        javafx.application.Application.launch(DesktopWindow.class, args);
    }
}
