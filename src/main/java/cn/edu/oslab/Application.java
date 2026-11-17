package cn.edu.oslab;

import cn.edu.oslab.core.FileSystem;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Path;

@SpringBootApplication
public class Application {
    public static void main(String[] args) throws Exception {
        if(java.util.Arrays.asList(args).contains("--self-test")){SelfTest.main(new String[0]);return;}
        SpringApplication.run(Application.class,args);
    }
    @Bean(destroyMethod="close") FileSystem fileSystem(@Value("${fat.disk}")String disk){return new FileSystem(Path.of(disk));}
}
