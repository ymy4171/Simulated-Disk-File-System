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
        command(200,Map.of("operation","delete_file","path","/a"));
        command(200,Map.of("operation","md","path","/doc"));
        command(200,Map.of("operation","md","path","/doc/sub"));
        assertEquals(1,((java.util.List<?>)command(200,Map.of("operation","dir","path","/doc")).get("result")).size());
        command(200,Map.of("operation","create_file","path","/doc/sub/a.tx","attribute",4,"mode","r"));
        command(200,Map.of("operation","write_file","path","/doc/sub/a.tx","content","x".repeat(64)));
        command(400,Map.of("operation","read_file","path","/doc/sub/a.tx","count",32));
        command(400,Map.of("operation","delete_file","path","/doc/sub/a.tx"));
        command(400,Map.of("operation","change","path","/doc/sub/a.tx","attribute",5));
        command(200,Map.of("operation","close_file","path","/doc/sub/a.tx"));
        command(200,Map.of("operation","open_file","path","/doc/sub/a.tx","mode","r"));
        for(int i=0;i<2;i++)assertEquals("x".repeat(32),((Map<?,?>)command(200,Map.of("operation","read_file","path","/doc/sub/a.tx","count",32)).get("result")).get("text"));
        command(400,Map.of("operation","write_file","path","/doc/sub/a.tx","content","bad"));
        command(200,Map.of("operation","close_file","path","/doc/sub/a.tx"));
        command(200,Map.of("operation","write_file","path","/doc/sub/a.tx","content","中文"));
        command(200,Map.of("operation","close_file","path","/doc/sub/a.tx"));
        var content=(Map<?,?>)command(200,Map.of("operation","typefile","path","/doc/sub/a.tx")).get("result");
        assertEquals(70,content.get("bytes"));assertEquals("x".repeat(64)+"中文",content.get("text"));
        command(200,Map.of("operation","change","path","/doc/sub/a.tx","attribute",5));
        command(400,Map.of("operation","open_file","path","/doc/sub/a.tx","mode","w"));
        command(400,Map.of("operation","rd","path","/doc/sub"));
        assertEquals(200,client.getForEntity("/api/blocks/5",Map.class).getStatusCode().value());
        command(200,Map.of("operation","delete_file","path","/doc/sub/a.tx"));
        command(200,Map.of("operation","rd","path","/doc/sub"));
        command(200,Map.of("operation","rd","path","/doc"));
        assertEquals(123,client.getForObject("/api/state",Map.class).get("free"));
    }
    private Map<?,?> command(int status,Map<String,Object> body){
        var response=client.postForEntity("/api/operations",body,Map.class);
        assertEquals(status,response.getStatusCode().value(),body.toString());
        assertNotNull(response.getBody());assertEquals(status==200,response.getBody().get("ok"));return response.getBody();
    }
}
