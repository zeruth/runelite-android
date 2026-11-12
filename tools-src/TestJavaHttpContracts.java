// host-contract: patched-args=shadow patched-classpath=okhttp
// The JDK accepts a request URI with userinfo; the shadow rejects it, so only the shadow run asserts that.
// The shadow run has okhttp and the app's OkHttpWebSocketTransport on its class path, and asserts the
// WebSocket echo went through it, ping and pong included.
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.sun.net.httpserver.HttpServer;

public final class TestJavaHttpContracts {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}

    static byte[] readAll(InputStream input)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];
        for(int n;(n=input.read(buffer))>=0;)out.write(buffer,0,n);
        return out.toByteArray();
    }

    /** Sends the request through one BodyHandler and returns what it received as text. */
    interface Reader { String read(HttpClient client,HttpRequest request,String expected)throws Exception; }

    static final class Collect implements Flow.Subscriber<List<ByteBuffer>> {
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();final CompletableFuture<String> done=new CompletableFuture<>();
        public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
        public void onNext(List<ByteBuffer> buffers){for(ByteBuffer buffer:buffers){byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.write(part,0,part.length);}}
        public void onError(Throwable error){done.completeExceptionally(error);}
        public void onComplete(){done.complete(text());}
        String text(){return new String(bytes.toByteArray(),StandardCharsets.UTF_8);}
    }

    static final class Lines implements Flow.Subscriber<String> {
        final List<String> lines=new ArrayList<>();final CompletableFuture<String> done=new CompletableFuture<>();
        public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
        public void onNext(String line){lines.add(line);}
        public void onError(Throwable error){done.completeExceptionally(error);}
        public void onComplete(){done.complete(text());}
        String text(){return String.join("\n",lines);}
    }

    static Map<String,Reader> readers(Path temp){
        Map<String,Reader> readers=new LinkedHashMap<>();int[] serial={0};
        readers.put("ofString",(c,r,e)->{
            HttpResponse<String> response=c.send(r,HttpResponse.BodyHandlers.ofString());
            byte[] sent=e.getBytes(StandardCharsets.UTF_8);
            if(sent.length>0)check(response.headers().firstValueAsLong("content-length").getAsLong()==sent.length,"firstValueAsLong");
            check(response.headers().allValues("CONTENT-TYPE").equals(Collections.singletonList("text/plain; charset=UTF-8")),"allValues case-insensitive");
            check(response.version()==HttpClient.Version.HTTP_1_1&&response.request()!=null&&response.uri().getPath().equals("/echo"),"response info");
            return response.body();
        });
        readers.put("ofString(charset)",(c,r,e)->c.send(r,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body());
        readers.put("ofByteArray",(c,r,e)->new String(c.send(r,HttpResponse.BodyHandlers.ofByteArray()).body(),StandardCharsets.UTF_8));
        readers.put("ofInputStream",(c,r,e)->{try(InputStream in=c.send(r,HttpResponse.BodyHandlers.ofInputStream()).body()){return new String(readAll(in),StandardCharsets.UTF_8);}});
        readers.put("ofLines",(c,r,e)->{try(Stream<String> lines=c.send(r,HttpResponse.BodyHandlers.ofLines()).body()){return lines.collect(Collectors.joining("\n"));}});
        readers.put("ofFile",(c,r,e)->{
            Path file=temp.resolve("body-"+(serial[0]++)+".txt");Path got=c.send(r,HttpResponse.BodyHandlers.ofFile(file)).body();
            check(got.equals(file),"ofFile path");return new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
        });
        readers.put("ofFile(options)",(c,r,e)->{
            Path file=temp.resolve("body-"+(serial[0]++)+".txt");Files.write(file,"stale content longer than any body sent here, to be truncated".getBytes(StandardCharsets.UTF_8));
            c.send(r,HttpResponse.BodyHandlers.ofFile(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING));
            return new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
        });
        readers.put("ofFileDownload",(c,r,e)->{
            Path directory=Files.createDirectory(temp.resolve("download-"+(serial[0]++)));
            Path got=c.send(r,HttpResponse.BodyHandlers.ofFileDownload(directory,StandardOpenOption.CREATE,StandardOpenOption.WRITE)).body();
            check(got.equals(directory.resolve("echo.txt")),"ofFileDownload uses the attachment filename: "+got);
            return new String(Files.readAllBytes(got),StandardCharsets.UTF_8);
        });
        readers.put("ofByteArrayConsumer",(c,r,e)->{
            ByteArrayOutputStream out=new ByteArrayOutputStream();boolean[] ended={false};
            c.send(r,HttpResponse.BodyHandlers.ofByteArrayConsumer(part->{if(part.isPresent())out.write(part.get(),0,part.get().length);else ended[0]=true;}));
            check(ended[0],"ofByteArrayConsumer end signal");return new String(out.toByteArray(),StandardCharsets.UTF_8);
        });
        readers.put("ofPublisher",(c,r,e)->{
            Flow.Publisher<List<ByteBuffer>> body=c.send(r,HttpResponse.BodyHandlers.ofPublisher()).body();
            Collect collect=new Collect();body.subscribe(collect);return collect.done.get(3,TimeUnit.SECONDS);
        });
        readers.put("buffering",(c,r,e)->c.send(r,HttpResponse.BodyHandlers.buffering(HttpResponse.BodyHandlers.ofString(),3)).body());
        readers.put("discarding",(c,r,e)->{HttpResponse<Void> response=c.send(r,HttpResponse.BodyHandlers.discarding());return response.statusCode()==200&&response.body()==null?e:"discarding returned a body";});
        readers.put("replacing",(c,r,e)->"replaced".equals(c.send(r,HttpResponse.BodyHandlers.replacing("replaced")).body())?e:"replacing ignored its value");
        readers.put("mapping",(c,r,e)->c.send(r,(HttpResponse.BodyHandler<String>)info->HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray(),bytes->new String(bytes,StandardCharsets.UTF_8))).body());
        readers.put("fromSubscriber",(c,r,e)->{Collect collect=new Collect();c.send(r,HttpResponse.BodyHandlers.fromSubscriber(collect));return collect.done.get(3,TimeUnit.SECONDS);});
        readers.put("fromSubscriber(finisher)",(c,r,e)->c.send(r,HttpResponse.BodyHandlers.fromSubscriber(new Collect(),Collect::text)).body());
        readers.put("fromLineSubscriber",(c,r,e)->{Lines lines=new Lines();c.send(r,HttpResponse.BodyHandlers.fromLineSubscriber(lines));return lines.done.get(3,TimeUnit.SECONDS);});
        readers.put("fromLineSubscriber(finisher)",(c,r,e)->c.send(r,HttpResponse.BodyHandlers.fromLineSubscriber(new Lines(),Lines::text,"\n")).body());
        return readers;
    }

    static Map<String,Supplier<HttpRequest.BodyPublisher>> publishers(String text,Path file){
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);int half=bytes.length/2;
        byte[] padded=new byte[bytes.length+6];System.arraycopy(bytes,0,padded,3,bytes.length);
        Map<String,Supplier<HttpRequest.BodyPublisher>> publishers=new LinkedHashMap<>();
        publishers.put("ofString",()->HttpRequest.BodyPublishers.ofString(text));
        publishers.put("ofString(charset)",()->HttpRequest.BodyPublishers.ofString(text,StandardCharsets.UTF_8));
        publishers.put("ofByteArray",()->HttpRequest.BodyPublishers.ofByteArray(bytes));
        publishers.put("ofByteArray(offset)",()->HttpRequest.BodyPublishers.ofByteArray(padded,3,bytes.length));
        publishers.put("ofByteArrays",()->HttpRequest.BodyPublishers.ofByteArrays(Arrays.asList(Arrays.copyOfRange(bytes,0,half),new byte[0],Arrays.copyOfRange(bytes,half,bytes.length))));
        publishers.put("ofInputStream",()->HttpRequest.BodyPublishers.ofInputStream(()->new ByteArrayInputStream(bytes)));
        publishers.put("ofFile",()->{try{return HttpRequest.BodyPublishers.ofFile(file);}catch(FileNotFoundException e){throw new UncheckedIOException(e);}});
        publishers.put("fromPublisher",()->HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofByteArray(bytes)));
        if(bytes.length>0)publishers.put("fromPublisher(length)",()->HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofByteArray(bytes),bytes.length));
        publishers.put("concat",()->HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(Arrays.copyOfRange(bytes,0,half)),HttpRequest.BodyPublishers.ofInputStream(()->new ByteArrayInputStream(Arrays.copyOfRange(bytes,half,bytes.length)))));
        return publishers;
    }

    public static void main(String[]args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),4);
        server.createContext("/echo",exchange->{
            byte[] body=readAll(exchange.getRequestBody());
            exchange.getResponseHeaders().set("Content-Type","text/plain; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Disposition","attachment; filename=\"echo.txt\"");
            exchange.getResponseHeaders().set("X-Method",exchange.getRequestMethod());
            exchange.sendResponseHeaders(200,body.length==0?-1:body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/redirect",exchange->{exchange.getResponseHeaders().set("Location","/echo");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/error",exchange->{byte[] body="error response".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(409,body.length);exchange.getResponseBody().write(body);exchange.close();});
        server.createContext("/credentials",exchange->{byte[] body=String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")).getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
        server.createContext("/cross",exchange->{exchange.getResponseHeaders().set("Location","http://localhost:"+server.getAddress().getPort()+"/credentials");exchange.sendResponseHeaders(302,-1);exchange.close();});
        String basic="Basic "+Base64.getEncoder().encodeToString("user:secret".getBytes(StandardCharsets.ISO_8859_1));
        server.createContext("/protected",exchange->{
            if(basic.equals(exchange.getRequestHeaders().getFirst("Authorization"))){byte[] body="ok".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);}
            else{exchange.getResponseHeaders().set("WWW-Authenticate","Basic realm=\"contract\"");exchange.sendResponseHeaders(401,-1);}
            exchange.close();
        });
        server.createContext("/slow",exchange->{try{Thread.sleep(600);}catch(InterruptedException ignored){}exchange.sendResponseHeaders(200,-1);exchange.close();});
        server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;}));server.start();
        WebSocketEcho echo=new WebSocketEcho();
        Path temp=Files.createTempDirectory("java-http-contracts");
        try{
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1).priority(1).executor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;})).build();
            check(client.version()==HttpClient.Version.HTTP_1_1&&client.followRedirects()==HttpClient.Redirect.NORMAL,"builder version/redirect");
            check(client.connectTimeout().get().equals(Duration.ofSeconds(2))&&client.executor().isPresent()&&!client.authenticator().isPresent(),"builder getters");
            check(HttpClient.newHttpClient().followRedirects()==HttpClient.Redirect.NEVER,"default redirect policy");
            HttpRequest request=HttpRequest.newBuilder(URI.create(base+"/echo")).POST(HttpRequest.BodyPublishers.ofString("日本語 test")).build();
            HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
            check(response.statusCode()==200&&response.body().equals("日本語 test"),"UTF8 POST");
            check(client.sendAsync(request,HttpResponse.BodyHandlers.ofByteArray()).get(3,TimeUnit.SECONDS).body().length>0,"async body");

            // Every BodyPublisher through every BodyHandler, for a multi-line non-ASCII body and an empty one.
            int pairs=0;
            Map<String,Reader> readers=readers(temp);
            for(String text:new String[]{"line one\n日本語 two\nthree",""}){
                Path source=temp.resolve("source-"+text.length()+".txt");Files.write(source,text.getBytes(StandardCharsets.UTF_8));
                for(Map.Entry<String,Supplier<HttpRequest.BodyPublisher>> publisher:publishers(text,source).entrySet()){
                    HttpRequest.BodyPublisher sample=publisher.getValue().get();long length=sample.contentLength();
                    check(length<0||length==text.getBytes(StandardCharsets.UTF_8).length,publisher.getKey()+" contentLength "+length);
                    for(Map.Entry<String,Reader> reader:readers.entrySet()){
                        String pair=publisher.getKey()+" -> "+reader.getKey()+" ["+text.length()+" chars]";
                        HttpRequest echoed=HttpRequest.newBuilder(URI.create(base+"/echo")).timeout(Duration.ofSeconds(5)).POST(publisher.getValue().get()).build();
                        String got;
                        try{got=reader.getValue().read(client,echoed,text);}catch(Exception|AssertionError e){throw new AssertionError(pair+" failed",e);}
                        check(text.equals(got),pair+" returned "+got);
                        pairs++;
                    }
                }
            }
            check(pairs>300,"pair count "+pairs);

            response=client.send(HttpRequest.newBuilder(URI.create(base+"/echo")).PUT(HttpRequest.BodyPublishers.ofString("put")).build(),HttpResponse.BodyHandlers.ofString());
            check(response.body().equals("put")&&response.headers().firstValue("X-Method").get().equals("PUT"),"PUT");
            response=client.send(HttpRequest.newBuilder(URI.create(base+"/echo")).method("DELETE",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
            check(response.headers().firstValue("x-method").get().equals("DELETE"),"method(DELETE, noBody)");
            HttpRequest built=HttpRequest.newBuilder(URI.create(base+"/echo")).header("X-A","1").header("X-A","2").headers("X-B","3","X-C","4").setHeader("X-B","5")
                .timeout(Duration.ofSeconds(7)).expectContinue(false).version(HttpClient.Version.HTTP_1_1).GET().build();
            check(built.headers().allValues("x-a").equals(Arrays.asList("1","2"))&&built.headers().firstValue("X-B").get().equals("5")&&built.headers().map().size()==3,"request headers");
            check(built.timeout().get().equals(Duration.ofSeconds(7))&&built.method().equals("GET")&&!built.bodyPublisher().isPresent()&&built.version().get()==HttpClient.Version.HTTP_1_1,"request builder getters");
            HttpRequest copied=HttpRequest.newBuilder(built,(name,value)->!name.equalsIgnoreCase("X-C")).build();
            check(!copied.headers().firstValue("X-C").isPresent()&&copied.headers().firstValue("X-B").isPresent(),"newBuilder(request, filter)");
            Map<String,List<String>> raw=new HashMap<>();raw.put("Keep",Arrays.asList("a","b"));raw.put("Drop",Collections.singletonList("c"));
            HttpHeaders filtered=HttpHeaders.of(raw,(name,value)->!name.equals("Drop")&&!value.equals("b"));
            check(filtered.map().size()==1&&filtered.allValues("keep").equals(Collections.singletonList("a"))&&filtered.allValues("Drop").isEmpty(),"HttpHeaders.of filter");

            HttpResponse<String> redirected=client.send(HttpRequest.newBuilder(URI.create(base+"/redirect")).build(),HttpResponse.BodyHandlers.ofString());
            check(redirected.statusCode()==200&&redirected.previousResponse().get().statusCode()==302,"redirect history");
            response=client.send(HttpRequest.newBuilder(URI.create(base+"/error")).build(),HttpResponse.BodyHandlers.ofString());
            check(response.statusCode()==409&&response.body().equals("error response"),"HTTP error body");
            try{client.send(HttpRequest.newBuilder(URI.create(base+"/slow")).timeout(Duration.ofMillis(80)).build(),HttpResponse.BodyHandlers.discarding());throw new AssertionError("timeout missing");}
            catch(HttpTimeoutException expected){}
            try{client.sendAsync(HttpRequest.newBuilder(URI.create(base+"/slow")).timeout(Duration.ofMillis(80)).build(),HttpResponse.BodyHandlers.discarding()).get(3,TimeUnit.SECONDS);throw new AssertionError("async timeout missing");}
            catch(ExecutionException expected){check(expected.getCause() instanceof HttpTimeoutException,"async timeout cause "+expected.getCause());}
            InputStream stream=client.send(request,HttpResponse.BodyHandlers.ofInputStream()).body();
            check(TestHttpServerContracts.read(stream).equals("日本語 test"),"streaming body");stream.close();
            response=client.send(HttpRequest.newBuilder(URI.create(base+"/cross")).header("Authorization","Bearer local-test").build(),HttpResponse.BodyHandlers.ofString());
            check(response.body().equals("null"),"cross-origin authorization stripped");
            authenticator(base,server.getAddress().getPort());
            try{HttpRequest.newBuilder(URI.create(base)).header("X-Test","bad\r\nInjected: yes");throw new AssertionError("header injection accepted");}catch(IllegalArgumentException expected){}
            if(args.length>0&&args[0].equals("shadow")){try{HttpRequest.newBuilder(URI.create("http://user:pass@localhost/"));throw new AssertionError("userinfo accepted");}catch(IllegalArgumentException expected){}}
            try{HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.noBody(),0);throw new AssertionError("nonpositive publisher length accepted");}catch(IllegalArgumentException expected){}
            try{HttpResponse.BodyHandlers.buffering(HttpResponse.BodyHandlers.ofString(),0);throw new AssertionError("zero buffer accepted");}catch(IllegalArgumentException expected){}
            try{HttpResponse.BodyHandlers.ofFile(temp.resolve("x"),StandardOpenOption.READ);throw new AssertionError("READ option accepted");}catch(IllegalArgumentException expected){}
            CompletableFuture<HttpResponse<String>> cancelled=client.sendAsync(HttpRequest.newBuilder(URI.create(base+"/slow")).build(),HttpResponse.BodyHandlers.ofString());
            cancelled.cancel(true);check(cancelled.isCancelled(),"cancel completes immediately");
            check(client.send(request,HttpResponse.BodyHandlers.ofString()).body().equals("日本語 test"),"request works after cancellation");

            webSocket(client,echo.port,args.length>0&&args[0].equals("shadow"));
            System.out.println("PASS: Java HTTP "+pairs+" publisher/handler pairs, builders, headers, redirects, errors, timeouts, streaming, credential stripping, invalid input, cancellation, WebSocket echo");
        }finally{
            server.stop(0);echo.close();
            try(Stream<Path> files=Files.walk(temp)){files.sorted(Comparator.reverseOrder()).forEach(path->path.toFile().delete());}
        }
    }

    /** A Basic 401 challenge is answered through the Authenticator, which sees the requesting host, port, realm, scheme and type. */
    static void authenticator(String base,int port)throws Exception{
        List<String> asked=new CopyOnWriteArrayList<>();
        Authenticator authenticator=new Authenticator(){
            protected PasswordAuthentication getPasswordAuthentication(){
                asked.add(getRequestingHost()+"|"+getRequestingPort()+"|"+getRequestingPrompt()+"|"+getRequestingScheme()+"|"+getRequestorType()+"|"+getRequestingURL());
                return new PasswordAuthentication("user","secret".toCharArray());
            }
        };
        HttpClient client=HttpClient.newBuilder().authenticator(authenticator).version(HttpClient.Version.HTTP_1_1).build();
        check(client.authenticator().get()==authenticator,"authenticator getter");
        HttpResponse<String> response=client.send(HttpRequest.newBuilder(URI.create(base+"/protected")).build(),HttpResponse.BodyHandlers.ofString());
        check(response.statusCode()==200&&response.body().equals("ok"),"Basic challenge answered: "+response.statusCode());
        check(!asked.isEmpty()&&asked.get(0).equals("127.0.0.1|"+port+"|contract|basic|SERVER|"+base+"/protected"),"authenticator requesting fields "+asked);
        response=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(HttpRequest.newBuilder(URI.create(base+"/protected")).build(),HttpResponse.BodyHandlers.ofString());
        check(response.statusCode()==401,"no authenticator leaves the 401: "+response.statusCode());
    }

    static void webSocket(HttpClient client,int port,boolean shadow)throws Exception{
        BlockingQueue<String> events=new LinkedBlockingQueue<>();String[] openedBy={""};
        WebSocket.Listener listener=new WebSocket.Listener(){
            final StringBuilder text=new StringBuilder();final ByteArrayOutputStream binary=new ByteArrayOutputStream();
            public void onOpen(WebSocket socket){
                // The transport's open callback is on the stack: tells okhttp from the socket fallback.
                for(StackTraceElement frame:new Throwable().getStackTrace())if(frame.getClassName().startsWith("net.runelite.mp.http.OkHttpWebSocketTransport"))openedBy[0]="okhttp";
                events.add("open");socket.request(1);
            }
            public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){
                text.append(data);if(last){events.add("text:"+text);text.setLength(0);}socket.request(1);return null;
            }
            public CompletionStage<?> onBinary(WebSocket socket,ByteBuffer data,boolean last){
                byte[] part=new byte[data.remaining()];data.get(part);binary.write(part,0,part.length);
                if(last){events.add("binary:"+Arrays.hashCode(binary.toByteArray())+":"+binary.size());binary.reset();}socket.request(1);return null;
            }
            public CompletionStage<?> onPing(WebSocket socket,ByteBuffer message){events.add("ping:"+StandardCharsets.UTF_8.decode(message));socket.request(1);return null;}
            public CompletionStage<?> onPong(WebSocket socket,ByteBuffer message){events.add("pong:"+StandardCharsets.UTF_8.decode(message));socket.request(1);return null;}
            public CompletionStage<?> onClose(WebSocket socket,int code,String reason){events.add("close:"+code+":"+reason);return null;}
            public void onError(WebSocket socket,Throwable error){events.add("error:"+error);}
        };
        WebSocket socket=client.newWebSocketBuilder().subprotocols("echo","other").connectTimeout(Duration.ofSeconds(2)).header("X-Client","contract")
            .buildAsync(URI.create("ws://127.0.0.1:"+port+"/socket"),listener).get(5,TimeUnit.SECONDS);
        check(socket.getSubprotocol().equals("echo"),"negotiated subprotocol "+socket.getSubprotocol());
        expect(events,"open");
        if(shadow)check(openedBy[0].equals("okhttp"),"shadow WebSocket did not run over OkHttpWebSocketTransport");
        expect(events,"ping:server hello");
        socket.sendText("hello 日本語",true).get(3,TimeUnit.SECONDS);
        expect(events,"text:hello 日本語");
        socket.sendText("frag",false).get(3,TimeUnit.SECONDS);socket.sendText("mented",true).get(3,TimeUnit.SECONDS);
        expect(events,"text:fragmented");
        byte[] payload=new byte[70000];for(int i=0;i<payload.length;i++)payload[i]=(byte)(i*31);
        socket.sendBinary(ByteBuffer.wrap(payload),true).get(3,TimeUnit.SECONDS);
        expect(events,"binary:"+Arrays.hashCode(payload)+":"+payload.length);
        socket.sendPing(ByteBuffer.wrap("probe".getBytes(StandardCharsets.UTF_8))).get(3,TimeUnit.SECONDS);
        expect(events,"pong:probe");
        socket.sendPong(ByteBuffer.wrap("unsolicited".getBytes(StandardCharsets.UTF_8))).get(3,TimeUnit.SECONDS);
        socket.sendText("after pong",true).get(3,TimeUnit.SECONDS);
        expect(events,"text:after pong");
        socket.sendClose(WebSocket.NORMAL_CLOSURE,"bye").get(3,TimeUnit.SECONDS);
        check(socket.isOutputClosed(),"output closed after sendClose");
        expect(events,"close:1000:bye");
        try{socket.sendText("late",true).get(3,TimeUnit.SECONDS);throw new AssertionError("send after close accepted");}catch(ExecutionException expected){}
        check(handshakeRejected(client,port),"handshake failure carries the 404 response");
    }

    static boolean handshakeRejected(HttpClient client,int port)throws Exception{
        WebSocket.Listener quiet=new WebSocket.Listener(){};
        try{client.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:"+port+"/missing"),quiet).get(5,TimeUnit.SECONDS);return false;}
        catch(ExecutionException expected){
            Throwable cause=expected.getCause();
            check(cause instanceof WebSocketHandshakeException,"handshake failure type "+cause);
            return ((WebSocketHandshakeException)cause).getResponse().statusCode()==404;
        }
    }

    static void expect(BlockingQueue<String> events,String wanted)throws InterruptedException{
        String got=events.poll(5,TimeUnit.SECONDS);
        check(wanted.equals(got),"WebSocket event: wanted "+wanted+", got "+got);
    }

    /** A minimal RFC 6455 server: pings once, echoes data frames as they arrive, answers ping and close. */
    static final class WebSocketEcho implements Closeable {
        final ServerSocket listening=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));final int port=listening.getLocalPort();
        WebSocketEcho()throws IOException{
            Thread acceptor=new Thread(()->{
                while(!listening.isClosed()){
                    try{Socket connection=listening.accept();Thread worker=new Thread(()->serve(connection),"ws-echo");worker.setDaemon(true);worker.start();}
                    catch(IOException closed){return;}
                }
            },"ws-accept");
            acceptor.setDaemon(true);acceptor.start();
        }
        public void close()throws IOException{listening.close();}

        static String line(DataInputStream input)throws IOException{
            StringBuilder line=new StringBuilder();
            for(int c;(c=input.read())>=0&&c!='\n';)if(c!='\r')line.append((char)c);
            return line.toString();
        }

        static void frame(OutputStream output,int first,byte[] payload)throws IOException{
            ByteArrayOutputStream frame=new ByteArrayOutputStream();frame.write(first);
            if(payload.length<126)frame.write(payload.length);
            else if(payload.length<65536){frame.write(126);frame.write(payload.length>>>8);frame.write(payload.length);}
            else{frame.write(127);for(int shift=56;shift>=0;shift-=8)frame.write((int)((long)payload.length>>>shift));}
            frame.write(payload,0,payload.length);
            synchronized(output){output.write(frame.toByteArray());output.flush();}
        }

        void serve(Socket connection){
            try(Socket socket=connection){
                DataInputStream input=new DataInputStream(new BufferedInputStream(socket.getInputStream()));OutputStream output=socket.getOutputStream();
                String[] start=line(input).split(" ");Map<String,String> fields=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                for(String header=line(input);!header.isEmpty();header=line(input)){int colon=header.indexOf(':');if(colon>0)fields.put(header.substring(0,colon).trim(),header.substring(colon+1).trim());}
                if(start.length<2||!start[1].equals("/socket")||!"contract".equals(fields.get("X-Client"))){
                    output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));output.flush();return;
                }
                String accept=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((fields.get("Sec-WebSocket-Key")+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.ISO_8859_1)));
                boolean offered=Arrays.stream(String.valueOf(fields.get("Sec-WebSocket-Protocol")).split(",")).anyMatch(p->p.trim().equals("echo"));
                output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n"
                    +(offered?"Sec-WebSocket-Protocol: echo\r\n":"")+"\r\n").getBytes(StandardCharsets.ISO_8859_1));
                output.flush();
                frame(output,0x89,"server hello".getBytes(StandardCharsets.UTF_8));
                while(true){
                    int first=input.read();if(first<0)return;
                    int second=input.readUnsignedByte();long length=second&0x7F;
                    if(length==126)length=input.readUnsignedShort();else if(length==127)length=input.readLong();
                    byte[] mask=new byte[4];if((second&0x80)!=0)input.readFully(mask);
                    byte[] payload=new byte[(int)length];input.readFully(payload);
                    for(int i=0;i<payload.length;i++)payload[i]^=mask[i&3];
                    int opcode=first&0x0F;
                    if(opcode==0x9)frame(output,0x8A,payload);
                    else if(opcode==0xA)continue;
                    else if(opcode==0x8){frame(output,0x88,payload);return;}
                    else frame(output,first&0x8F,payload);
                }
            }catch(Exception error){
                // A peer that drops the connection ends this echo only.
            }
        }
    }
}
