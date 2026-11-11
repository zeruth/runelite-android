// host-contract: patched-args=shadow
// The JDK accepts a request URI with userinfo; the shadow rejects it, so only the shadow run asserts that.
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import com.sun.net.httpserver.HttpServer;

public final class TestJavaHttpContracts {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[]args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),4);
        server.createContext("/echo",exchange->{
            byte[] body=TestHttpServerContracts.read(exchange.getRequestBody()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/plain; charset=UTF-8");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/redirect",exchange->{exchange.getResponseHeaders().set("Location","/echo");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/error",exchange->{byte[] body="error response".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(409,body.length);exchange.getResponseBody().write(body);exchange.close();});
        server.createContext("/credentials",exchange->{byte[] body=String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")).getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
        server.createContext("/cross",exchange->{exchange.getResponseHeaders().set("Location","http://localhost:"+server.getAddress().getPort()+"/credentials");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/slow",exchange->{try{Thread.sleep(600);}catch(InterruptedException ignored){}exchange.sendResponseHeaders(200,-1);exchange.close();});
        server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;}));server.start();
        try{
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest request=HttpRequest.newBuilder(URI.create(base+"/echo")).POST(HttpRequest.BodyPublishers.ofString("日本語 test")).build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
            check(response.statusCode()==200&&response.body().equals("日本語 test"),"UTF8 POST");
            check(client.sendAsync(request,HttpResponse.BodyHandlers.ofByteArray()).get(3,TimeUnit.SECONDS).body().length>0,"async body");
            HttpResponse<String> redirected=client.send(HttpRequest.newBuilder(URI.create(base+"/redirect")).build(),HttpResponse.BodyHandlers.ofString());
            check(redirected.statusCode()==200&&redirected.previousResponse().get().statusCode()==302,"redirect history");
            response=client.send(HttpRequest.newBuilder(URI.create(base+"/error")).build(),HttpResponse.BodyHandlers.ofString());
            check(response.statusCode()==409&&response.body().equals("error response"),"HTTP error body");
            try{client.send(HttpRequest.newBuilder(URI.create(base+"/slow")).timeout(Duration.ofMillis(80)).build(),HttpResponse.BodyHandlers.discarding());throw new AssertionError("timeout missing");}catch(java.io.IOException expected){}
            java.io.InputStream stream=client.send(request,HttpResponse.BodyHandlers.ofInputStream()).body();
            check(TestHttpServerContracts.read(stream).equals("日本語 test"),"streaming body");stream.close();
            response=client.send(HttpRequest.newBuilder(URI.create(base+"/cross")).header("Authorization","Bearer local-test").build(),HttpResponse.BodyHandlers.ofString());
            check(response.body().equals("null"),"cross-origin authorization stripped");
            try{HttpRequest.newBuilder(URI.create(base)).header("X-Test","bad\r\nInjected: yes");throw new AssertionError("header injection accepted");}catch(IllegalArgumentException expected){}
            if(args.length>0&&args[0].equals("shadow")){try{HttpRequest.newBuilder(URI.create("http://user:pass@localhost/"));throw new AssertionError("userinfo accepted");}catch(IllegalArgumentException expected){}}
            try{HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.noBody(),0);throw new AssertionError("nonpositive publisher length accepted");}catch(IllegalArgumentException expected){}
            CompletableFuture<HttpResponse<String>> cancelled=client.sendAsync(HttpRequest.newBuilder(URI.create(base+"/slow")).build(),HttpResponse.BodyHandlers.ofString());
            cancelled.cancel(true);check(cancelled.isCancelled(),"cancel completes immediately");
            check(client.send(request,HttpResponse.BodyHandlers.ofString()).body().equals("日本語 test"),"request works after cancellation");
            System.out.println("PASS: Java HTTP sync/async POST, UTF8, redirects, errors, timeout, streaming, credential stripping, invalid input, cancellation");
        }finally{server.stop(0);}
    }
}
