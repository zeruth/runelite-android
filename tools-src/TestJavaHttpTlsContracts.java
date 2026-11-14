// host-contract: fixture=keystore
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.security.*;
import javax.net.ssl.*;
import com.sun.net.httpserver.*;

public final class TestJavaHttpTlsContracts {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[]args)throws Exception{
        KeyStore store=KeyStore.getInstance("PKCS12");try(InputStream in=new FileInputStream(args[0])){store.load(in,"test-only".toCharArray());}
        KeyManagerFactory keys=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());keys.init(store,"test-only".toCharArray());
        TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(store);
        SSLContext context=SSLContext.getInstance("TLS");context.init(keys.getKeyManagers(),trust.getTrustManagers(),null);
        HttpsServer secure=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),4);secure.setHttpsConfigurator(new HttpsConfigurator(context));
        HttpServer plain=HttpServer.create(new InetSocketAddress("127.0.0.1",0),4);
        plain.createContext("/",exchange->{byte[] bytes=String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")).getBytes("UTF-8");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        secure.createContext("/ok",exchange->{exchange.sendResponseHeaders(200,-1);exchange.close();});
        secure.createContext("/down",exchange->{exchange.getResponseHeaders().set("Location","http://localhost:"+plain.getAddress().getPort()+"/");exchange.sendResponseHeaders(302,-1);exchange.close();});
        secure.start();plain.start();
        try{
            String uri="https://localhost:"+secure.getAddress().getPort();
            java.net.http.HttpClient client=java.net.http.HttpClient.newBuilder().sslContext(context).followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> reply=client.send(HttpRequest.newBuilder(URI.create(uri+"/ok")).build(),HttpResponse.BodyHandlers.ofString());
            check(reply.statusCode()==200&&reply.sslSession().isPresent(),"trusted TLS and session");
            try{client.send(HttpRequest.newBuilder(URI.create(uri.replace("localhost","127.0.0.1")+"/ok")).build(),HttpResponse.BodyHandlers.ofString());throw new AssertionError("hostname verification bypass");}catch(IOException expected){}
            reply=client.send(HttpRequest.newBuilder(URI.create(uri+"/down")).header("Authorization","Bearer local-test").build(),HttpResponse.BodyHandlers.ofString());
            check(reply.statusCode()==302,"NORMAL blocks TLS downgrade");
            client=java.net.http.HttpClient.newBuilder().sslContext(context).followRedirects(java.net.http.HttpClient.Redirect.ALWAYS).build();
            reply=client.send(HttpRequest.newBuilder(URI.create(uri+"/down")).header("Authorization","Bearer local-test").build(),HttpResponse.BodyHandlers.ofString());
            check(reply.statusCode()==200&&reply.body().equals("null"),"ALWAYS strips auth on explicit downgrade");
            System.out.println("PASS: TLS trust/hostname/session, downgrade policy and credential stripping");
        }finally{secure.stop(0);plain.stop(0);}
    }
}
