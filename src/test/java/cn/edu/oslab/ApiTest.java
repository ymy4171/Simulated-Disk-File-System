package cn.edu.oslab;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiTest {
    static final Path folder;
    static {try {folder=Files.createTempDirectory("fat-api-test-");}catch(Exception e){throw new RuntimeException(e);}}
    @DynamicPropertySource static void disk(DynamicPropertyRegistry r){r.add("fat.disk",()->folder.resolve("disk.img").toString());}
    @Autowired TestRestTemplate client;
    @Test void browserApiFlow(){
        assertTrue(client.getForObject("/",String.class).contains("FAT Lab"));
        assertEquals(false,client.getForObject("/api/state",Map.class).get("ready"));
        assertEquals(400,client.postForEntity("/api/operations",Map.of("operation","initialize"),Map.class).getStatusCode().value());
        assertEquals(200,client.postForEntity("/api/operations",Map.of("operation","initialize","confirmed",true),Map.class).getStatusCode().value());
        assertEquals(200,client.postForEntity("/api/operations",Map.of("operation","create_file","path","/a","attribute",4),Map.class).getStatusCode().value());
        assertEquals(200,client.postForEntity("/api/operations",Map.of("operation","write_file","path","/a","content","hello"),Map.class).getStatusCode().value());
        assertEquals(200,client.postForEntity("/api/operations",Map.of("operation","close_all"),Map.class).getStatusCode().value());
        var response=client.postForObject("/api/operations",Map.of("operation","typefile","path","/a"),Map.class);
        assertEquals("hello",((Map<?,?>)response.get("result")).get("text"));
        assertEquals(400,client.getForEntity("/api/blocks/-1",Map.class).getStatusCode().value());
        assertEquals(400,client.postForEntity("/api/operations",Map.of("operation","unknown"),Map.class).getStatusCode().value());
    }
}
