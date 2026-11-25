package cn.edu.oslab.desktop;

import cn.edu.oslab.core.FileSystem;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import java.nio.file.*;

public final class DesktopWindow extends javafx.application.Application {
    private ConfigurableApplicationContext context;
    private boolean closing;

    @Override public void start(Stage stage) {
        BorderPane root = new BorderPane(new Label("正在启动模拟磁盘文件系统…"));
        stage.setTitle("模拟磁盘文件系统 · FAT Lab");
        stage.setScene(new Scene(root, 1440, 950));
        stage.setMinWidth(900); stage.setMinHeight(650);
        stage.setOnCloseRequest(event -> {
            event.consume();
            if (context == null || closing) return;
            closing = true; root.setDisable(true);
            new Thread(() -> {
                try {
                    // Close under the same monitor as HTTP operations. Failure keeps the app alive.
                    context.getBean(FileSystem.class).close();
                    context.close();
                    Platform.runLater(() -> {stage.hide(); Platform.exit();});
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        closing = false; root.setDisable(false);
                        alert(stage, Alert.AlertType.ERROR, "保存失败，窗口未关闭", e.getMessage()).showAndWait();
                    });
                }
            }, "desktop-save").start();
        });
        stage.show();
        new Thread(() -> {
            try {
                String base = System.getenv("LOCALAPPDATA");
                Path disk = Path.of(getParameters().getNamed().getOrDefault("disk",
                        Path.of(base == null ? System.getProperty("user.home") : base, "FatLab", "disk.img").toString())).toAbsolutePath();
                Files.createDirectories(disk.getParent());
                SpringApplication app = new SpringApplication(cn.edu.oslab.Application.class);
                app.setHeadless(false);
                context = app.run("--server.port=0", "--server.address=127.0.0.1", "--fat.disk=" + disk,
                        "--logging.file.name=" + disk.getParent().resolve("desktop.log"));
                int port = ((ServletWebServerApplicationContext)context).getWebServer().getPort();
                Platform.runLater(() -> {
                    WebView web = new WebView();
                    web.setFontSmoothingType(javafx.scene.text.FontSmoothingType.LCD);
                    web.getEngine().setConfirmHandler(message -> alert(stage, Alert.AlertType.CONFIRMATION,
                            "确认操作", message).showAndWait().filter(ButtonType.OK::equals).isPresent());
                    web.getEngine().setOnAlert(event -> alert(stage, Alert.AlertType.INFORMATION, "提示", event.getData()).showAndWait());
                    web.getEngine().getLoadWorker().stateProperty().addListener((obs, old, value) -> {
                        if (value == Worker.State.FAILED) alert(stage, Alert.AlertType.ERROR, "页面加载失败",
                                "请关闭软件后重试，日志位于：" + disk.getParent()).showAndWait();
                    });
                    Label location = new Label("数据文件：" + disk + "    |    关闭窗口自动保存");
                    location.setStyle("-fx-padding: 7; -fx-text-fill: #bbbbbb; -fx-background-color: #202124;");
                    root.setCenter(web); root.setBottom(location);
                    web.getEngine().load("http://127.0.0.1:" + port + "/");
                });
            } catch (Exception e) {
                if (context != null) context.close();
                Platform.runLater(() -> {
                    alert(stage, Alert.AlertType.ERROR, "启动失败", e.toString()).showAndWait();
                    stage.hide(); Platform.exit();
                });
            }
        }, "desktop-start").start();
    }

    private static Alert alert(Stage owner, Alert.AlertType type, String title, String text) {
        Alert alert = new Alert(type); alert.initOwner(owner); alert.setTitle(title);
        alert.setHeaderText(title);
        Label message = new Label(text); message.setWrapText(true); message.setMaxWidth(560);
        alert.getDialogPane().setContent(message); alert.getDialogPane().setMinWidth(600);
        return alert;
    }
}
