package gprobot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;

public class StatServer {


    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String cacheControl="public, max-age=3600";
            byte[] content = new byte[0];
            int status = 200;
            String path = t.getRequestURI().getPath();
            File f = new File(".", path);
            if (f.canRead()) {
                content = Files.readAllBytes(f.toPath());
                cacheControl = "no-cache, no-store, must-revalidate";
            } else {
                try (InputStream is = getClass().getResourceAsStream("/stat-server" + path)) {
                    if (is == null) throw new Exception("not found");
                    content = new byte[is.available()];
                    int len = 0;
                    while (len < content.length)
                        len += is.read(content,len, content.length-len);
                } catch (Exception e) {
                    content = "Ressource not found".getBytes();
                    status = 404;
                }
            }
            t.getResponseHeaders().set("Cache-Control", cacheControl);
            t.sendResponseHeaders(status, content.length);

            OutputStream os = t.getResponseBody();
            os.write(content);
            os.close();
        }
    }
}
