/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.net.http;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.*;

/** Java 11 request API backed by Android's HTTP(S) transport and TLS verification. */
public abstract class HttpClient {
    public enum Version { HTTP_1_1, HTTP_2 }
    public enum Redirect { NEVER, ALWAYS, NORMAL }
    protected HttpClient() {}
    public static HttpClient newHttpClient() { return newBuilder().build(); }
    public static Builder newBuilder() { return new ClientBuilder(); }
    public abstract Optional<CookieHandler> cookieHandler();
    public abstract Optional<Duration> connectTimeout();
    public abstract Redirect followRedirects();
    public abstract Optional<ProxySelector> proxy();
    public abstract SSLContext sslContext();
    public abstract SSLParameters sslParameters();
    public abstract Optional<Authenticator> authenticator();
    public abstract Version version();
    public abstract Optional<Executor> executor();
    public abstract <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException;
    public abstract <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler);
    // Pushes are never offered: the transport is HTTP/1.1, so the handler is never called.
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushHandler) { return sendAsync(request, handler); }
    public WebSocket.Builder newWebSocketBuilder() { throw new UnsupportedOperationException("java.net.http.HttpClient.newWebSocketBuilder()"); }
    public interface Builder {
        Builder cookieHandler(CookieHandler handler); Builder connectTimeout(Duration timeout); Builder sslContext(SSLContext context);
        Builder sslParameters(SSLParameters parameters); Builder executor(Executor executor); Builder followRedirects(Redirect policy);
        Builder version(Version version); Builder priority(int priority); Builder proxy(ProxySelector proxy); Builder authenticator(Authenticator authenticator);
        HttpClient build();
    }
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(8, 8, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(64), task -> { Thread thread = new Thread(task,"HttpClient-worker"); thread.setDaemon(true); return thread; });
    static { WORKERS.allowCoreThreadTimeOut(true); }
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> { Thread thread = new Thread(task,"HttpClient-timeout"); thread.setDaemon(true); return thread; });
    static final class ClientBuilder implements Builder {
        CookieHandler cookies; Duration timeout; SSLContext ssl; SSLParameters parameters; Executor executor; Redirect redirects=Redirect.NEVER;
        Version version=Version.HTTP_2; ProxySelector proxy; Authenticator authenticator;
        public Builder cookieHandler(CookieHandler handler){cookies=Objects.requireNonNull(handler);return this;}
        public Builder connectTimeout(Duration timeout){if(timeout.isNegative()||timeout.isZero())throw new IllegalArgumentException("Positive timeout required");this.timeout=timeout;return this;}
        public Builder sslContext(SSLContext context){ssl=Objects.requireNonNull(context);return this;}
        public Builder sslParameters(SSLParameters parameters){this.parameters=copy(Objects.requireNonNull(parameters));return this;}
        public Builder executor(Executor executor){this.executor=Objects.requireNonNull(executor);return this;}
        public Builder followRedirects(Redirect policy){redirects=Objects.requireNonNull(policy);return this;}
        public Builder version(Version version){this.version=Objects.requireNonNull(version);return this;}
        public Builder priority(int priority){if(priority<1||priority>256)throw new IllegalArgumentException("Priority outside 1..256");return this;}
        public Builder proxy(ProxySelector proxy){this.proxy=Objects.requireNonNull(proxy);return this;}
        public Builder authenticator(Authenticator authenticator){this.authenticator=Objects.requireNonNull(authenticator);return this;}
        public HttpClient build(){return new UrlClient(this);}
    }
    private static SSLParameters copy(SSLParameters source) {
        SSLParameters result=new SSLParameters();
        if(source.getCipherSuites()!=null)result.setCipherSuites(source.getCipherSuites());
        if(source.getProtocols()!=null)result.setProtocols(source.getProtocols());
        result.setEndpointIdentificationAlgorithm(source.getEndpointIdentificationAlgorithm());
        result.setNeedClientAuth(source.getNeedClientAuth()); if(!source.getNeedClientAuth())result.setWantClientAuth(source.getWantClientAuth());
        return result;
    }
    private static int millis(Duration timeout) { return (int)Math.min(Integer.MAX_VALUE,Math.max(1,timeout.toMillis())); }
    static final class UrlClient extends HttpClient {
        final CookieHandler cookies; final Duration timeout; final SSLContext ssl; final SSLParameters parameters; final Executor executor; final Redirect redirects; final Version version; final ProxySelector proxy; final Authenticator authenticator;
        UrlClient(ClientBuilder builder) {
            cookies=builder.cookies;timeout=builder.timeout;parameters=builder.parameters;executor=builder.executor;redirects=builder.redirects;version=builder.version;proxy=builder.proxy;authenticator=builder.authenticator;
            try { ssl=builder.ssl==null?SSLContext.getDefault():builder.ssl; }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        }
        public Optional<CookieHandler> cookieHandler(){return Optional.ofNullable(cookies);} public Optional<Duration> connectTimeout(){return Optional.ofNullable(timeout);}
        public Redirect followRedirects(){return redirects;} public Optional<ProxySelector> proxy(){return Optional.ofNullable(proxy);}
        public SSLContext sslContext(){return ssl;} public SSLParameters sslParameters(){return copy(parameters==null?ssl.getDefaultSSLParameters():parameters);}
        public Optional<Authenticator> authenticator(){return Optional.ofNullable(authenticator);} public Version version(){return version;} public Optional<Executor> executor(){return Optional.ofNullable(executor);}
        public WebSocket.Builder newWebSocketBuilder(){return new WebSocketImpl.BuilderImpl(this);}
        public <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler)throws IOException,InterruptedException {
            CompletableFuture<HttpResponse<T>> future=sendAsync(request,handler);
            try{return future.get();}catch(InterruptedException e){future.cancel(true);throw e;}
            catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof IOException)throw(IOException)cause;if(cause instanceof RuntimeException)throw(RuntimeException)cause;throw new IOException(cause);}
        }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,HttpResponse.BodyHandler<T> handler) {
            Objects.requireNonNull(request);Objects.requireNonNull(handler);
            CompletableFuture<HttpResponse<T>> result=new CompletableFuture<>();AtomicReference<HttpURLConnection> active=new AtomicReference<>();
            AtomicReference<Thread> worker = new AtomicReference<>();
            Runnable abort = () -> { HttpURLConnection connection=active.get(); if(connection!=null)connection.disconnect(); Thread thread=worker.get(); if(thread!=null)thread.interrupt(); };
            result.whenComplete((value,error)->{if(result.isCancelled())abort.run();});
            ScheduledFuture<?> deadline=request.timeout().isPresent()?TIMER.schedule(()->{
                if(result.completeExceptionally(new HttpTimeoutException("HTTP request timed out")))abort.run();
            },millis(request.timeout().get()),TimeUnit.MILLISECONDS):null;
            try { (executor==null?WORKERS:executor).execute(()->{
                worker.set(Thread.currentThread());
                try{exchange(request,handler,result,active);}catch(Throwable error){result.completeExceptionally(error);}
                finally{worker.set(null);if(deadline!=null)deadline.cancel(false);HttpURLConnection connection=active.getAndSet(null);if(connection!=null)connection.disconnect();Thread.interrupted();}
            }); } catch (RejectedExecutionException rejected) { if(deadline!=null)deadline.cancel(false);result.completeExceptionally(rejected); }
            return result;
        }
        private <T> void exchange(HttpRequest request,HttpResponse.BodyHandler<T> handler,CompletableFuture<HttpResponse<T>> result,AtomicReference<HttpURLConnection> active)throws Exception {
            URI uri=request.uri();String method=request.method();HttpRequest.BodyPublisher body=request.bodyPublisher().orElse(null);boolean sameOrigin=true;
            HttpResponse<T> previous = null;
            String authorization=null,proxyAuthorization=null;int authTries=0;
            for(int redirectsTaken=0;;redirectsTaken++) {
                if(result.isDone())return;
                Proxy selected=Proxy.NO_PROXY;
                ProxySelector selector=proxy==null?ProxySelector.getDefault():proxy;
                if(selector!=null){List<Proxy> choices=selector.select(uri);if(choices!=null&&!choices.isEmpty())selected=choices.get(0);}
                HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection(selected);active.set(connection);
                if (result.isDone() || Thread.currentThread().isInterrupted()) return;
                connection.setInstanceFollowRedirects(false);connection.setRequestMethod(method);
                if(timeout!=null)connection.setConnectTimeout(millis(timeout));
                if(request.timeout().isPresent()){connection.setReadTimeout(millis(request.timeout().get()));if(timeout==null)connection.setConnectTimeout(millis(request.timeout().get()));}
                ConfiguredSockets sockets = new ConfiguredSockets(ssl.getSocketFactory(),parameters);
                if(connection instanceof HttpsURLConnection)((HttpsURLConnection)connection).setSSLSocketFactory(sockets);
                Map<String,List<String>> sentHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                for(Map.Entry<String,List<String>> header:request.headers().map().entrySet()){
                    if(!sameOrigin&&(header.getKey().equalsIgnoreCase("Authorization")||header.getKey().equalsIgnoreCase("Proxy-Authorization")||header.getKey().equalsIgnoreCase("Cookie")||header.getKey().equalsIgnoreCase("Cookie2")))continue;
                    sentHeaders.put(header.getKey(),header.getValue());
                    for(String value:header.getValue())connection.addRequestProperty(header.getKey(),value);
                }
                if(cookies!=null)for(Map.Entry<String,List<String>> header:cookies.get(uri,Collections.unmodifiableMap(sentHeaders)).entrySet())for(String value:header.getValue())connection.addRequestProperty(header.getKey(),value);
                if(authorization!=null&&!sentHeaders.containsKey("Authorization"))connection.setRequestProperty("Authorization",authorization);
                if(proxyAuthorization!=null&&!sentHeaders.containsKey("Proxy-Authorization"))connection.setRequestProperty("Proxy-Authorization",proxyAuthorization);
                if(request.expectContinue())connection.setRequestProperty("Expect","100-continue");
                boolean readMethod = method.equals("GET") || method.equals("HEAD");
                if (readMethod && body != null && body.contentLength() != 0) throw net.runelite.awt.AwtUnsupported.fail("java.net.http.HttpClient.send(HttpRequest, BodyHandler)", "HttpURLConnection cannot send a GET/HEAD body");
                boolean sending=body!=null&&!readMethod;
                if(sending){connection.setDoOutput(true);if(body.contentLength()>=0)connection.setFixedLengthStreamingMode(body.contentLength());else connection.setChunkedStreamingMode(8192);}
                // Connect on its own so a connect timeout surfaces as the JDK's HttpConnectTimeoutException.
                try{connection.connect();}catch(SocketTimeoutException e){HttpConnectTimeoutException timedOut=new HttpConnectTimeoutException("HTTP connect timed out");timedOut.initCause(e);throw timedOut;}
                if(sending)try(OutputStream output=connection.getOutputStream()){publish(body,output);}
                int status;
                try{status=connection.getResponseCode();}catch(SocketTimeoutException e){HttpTimeoutException timedOut=new HttpTimeoutException("request timed out");timedOut.initCause(e);throw timedOut;}
                HttpHeaders headers=HttpHeaders.of(connection.getHeaderFields(),(key,value)->true);
                SSLSession session = sockets.socket == null ? null : sockets.socket.getSession();
                if(cookies!=null)cookies.put(uri,connection.getHeaderFields());
                if((status==401||status==407)&&authenticator!=null&&authTries<3){
                    boolean forProxy=status==407;
                    String realm=basicRealm(headers.allValues(forProxy?"Proxy-Authenticate":"WWW-Authenticate"));
                    PasswordAuthentication credentials=realm==null?null:credentials(authenticator,uri,forProxy,realm,selected);
                    if(credentials!=null){
                        String token="Basic "+Base64.getEncoder().encodeToString((credentials.getUserName()+":"+new String(credentials.getPassword())).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                        if(forProxy)proxyAuthorization=token;else authorization=token;
                        authTries++;redirectsTaken--;drain(connection);connection.disconnect();continue;
                    }
                }
                String location=connection.getHeaderField("Location");
                if(location!=null&&redirects!=Redirect.NEVER&&(status==301||status==302||status==303||status==307||status==308)){
                    URI next=uri.resolve(location);boolean downgrade="https".equalsIgnoreCase(uri.getScheme())&&!"https".equalsIgnoreCase(next.getScheme());
                    if (next.getUserInfo() != null || next.getHost() == null) throw new IOException("Unsafe redirect URI");
                    if(("http".equalsIgnoreCase(next.getScheme())||"https".equalsIgnoreCase(next.getScheme()))&&!(redirects==Redirect.NORMAL&&downgrade)){
                        if(redirectsTaken>=5)throw new IOException("Too many HTTP redirects");
                        previous = new Response<T>(status, request, headers, null, session, uri, previous);
                        sameOrigin=sameOrigin&&Objects.equals(uri.getHost(),next.getHost())&&uri.getPort()==next.getPort()&&Objects.equals(uri.getScheme(),next.getScheme());
                        if(!sameOrigin)authorization=null;
                        if((status==303&&!method.equals("HEAD"))||((status==301||status==302)&&method.equals("POST"))){method="GET";body=null;}
                        connection.disconnect();uri=next;continue;
                    }
                }
                final URI finalUri=uri;final HttpResponse.BodySubscriber<T> subscriber=Objects.requireNonNull(handler.apply(new HttpResponse.ResponseInfo(){public int statusCode(){return status;}public HttpHeaders headers(){return headers;}public Version version(){return Version.HTTP_1_1;}}));
                final HttpResponse<T> prior = previous;
                InputStream raw=status>=400?connection.getErrorStream():connection.getInputStream();
                final InputStream input=raw==null?new ByteArrayInputStream(new byte[0]):raw;
                Demand demand=new Demand(input,subscriber::onError);subscriber.onSubscribe(demand);
                subscriber.getBody().whenComplete((value,error)->{
                    if(error!=null)result.completeExceptionally(error);
                    else result.complete(new Response<T>(status, request, headers, value, session, finalUri, prior));
                });
                try(InputStream stream=input){
                    while(demand.await()){
                        byte[] buffer=new byte[8192];int n=stream.read(buffer);
                        if(n<0){subscriber.onComplete();break;}subscriber.onNext(Collections.singletonList(ByteBuffer.wrap(buffer,0,n)));
                    }
                }catch(Throwable error){subscriber.onError(error);throw error;}
                return;
            }
        }
        // The JDK client answers Basic challenges only; any other scheme leaves the 401/407 to the caller.
        private static String basicRealm(List<String> challenges){
            for(String challenge:challenges){
                String trimmed=challenge.trim();
                if(trimmed.regionMatches(true,0,"Basic",0,5)&&(trimmed.length()==5||trimmed.charAt(5)==' ')){
                    java.util.regex.Matcher realm=java.util.regex.Pattern.compile("realm=\"([^\"]*)\"|realm=([^\\s,]+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(trimmed);
                    return realm.find()?(realm.group(1)!=null?realm.group(1):realm.group(2)):"";
                }
            }
            return null;
        }
        private static PasswordAuthentication credentials(Authenticator authenticator,URI uri,boolean forProxy,String realm,Proxy selected)throws IOException{
            String host=uri.getHost();int port=uri.getPort()>=0?uri.getPort():"https".equalsIgnoreCase(uri.getScheme())?443:80;
            if(forProxy&&selected.address() instanceof InetSocketAddress){InetSocketAddress address=(InetSocketAddress)selected.address();host=address.getHostString();port=address.getPort();}
            Authenticator.RequestorType type=forProxy?Authenticator.RequestorType.PROXY:Authenticator.RequestorType.SERVER;
            try{
                // JDK 9+ instance entry point fills the requesting host/port/realm the authenticator reads.
                // The JDK client names the scheme in lower case ("basic"), unlike HttpURLConnection.
                java.lang.reflect.Method instance=Authenticator.class.getMethod("requestPasswordAuthenticationInstance",String.class,InetAddress.class,int.class,String.class,String.class,String.class,URL.class,Authenticator.RequestorType.class);
                return (PasswordAuthentication)instance.invoke(authenticator,host,null,port,uri.getScheme(),realm,"basic",uri.toURL(),type);
            }catch(NoSuchMethodException older){
                return legacyCredentials(authenticator,host,port,uri,realm,type);
            }catch(ReflectiveOperationException e){throw new IOException("Authenticator failed",e.getCause()==null?e:e.getCause());}
        }
        /**
         * Android's Authenticator is the JDK 8 one, without requestPasswordAuthenticationInstance: do what
         * that JDK 9 method does, under the same lock, by filling the private requesting* fields the
         * protected getters read (libcore keeps OpenJDK 8's names) before calling getPasswordAuthentication.
         * Those fields are non-SDK; where hidden-API policy denies one, its getter keeps the value from
         * the last request, and the authenticator is still asked rather than the 401/407 returned.
         */
        private static PasswordAuthentication legacyCredentials(Authenticator authenticator,String host,int port,URI uri,String realm,Authenticator.RequestorType type)throws IOException{
            URL url=uri.toURL();
            synchronized(authenticator){
                requesting(authenticator,"requestingHost",host);requesting(authenticator,"requestingSite",null);
                requesting(authenticator,"requestingPort",port);requesting(authenticator,"requestingProtocol",uri.getScheme());
                requesting(authenticator,"requestingPrompt",realm);requesting(authenticator,"requestingScheme","basic");
                requesting(authenticator,"requestingURL",url);requesting(authenticator,"requestingAuthType",type);
                try{
                    java.lang.reflect.Method hook=Authenticator.class.getDeclaredMethod("getPasswordAuthentication");hook.setAccessible(true);
                    return (PasswordAuthentication)hook.invoke(authenticator);
                }catch(java.lang.reflect.InvocationTargetException e){throw new IOException("Authenticator failed",e.getCause());}
                catch(ReflectiveOperationException|RuntimeException e){throw new IOException("Authenticator unavailable",e);}
            }
        }
        private static void requesting(Authenticator authenticator,String name,Object value){
            try{java.lang.reflect.Field field=Authenticator.class.getDeclaredField(name);field.setAccessible(true);field.set(authenticator,value);}
            catch(ReflectiveOperationException|RuntimeException denied){
                // Hidden-API policy or a renamed libcore field: see legacyCredentials.
            }
        }
        private static void drain(HttpURLConnection connection){
            try(InputStream stream=connection.getErrorStream()){if(stream!=null){byte[] skip=new byte[4096];while(stream.read(skip)>=0){}}}catch(IOException ignored){}
        }
        private static void publish(HttpRequest.BodyPublisher body,OutputStream output)throws Exception {
            CompletableFuture<Void> complete=new CompletableFuture<>();
            body.subscribe(new Flow.Subscriber<ByteBuffer>(){
                Flow.Subscription subscription;
                public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(Long.MAX_VALUE);}
                public void onNext(ByteBuffer buffer){try{byte[] bytes=new byte[Math.min(8192,Math.max(1,buffer.remaining()))];while(buffer.hasRemaining()){int n=Math.min(bytes.length,buffer.remaining());buffer.get(bytes,0,n);output.write(bytes,0,n);}}catch(IOException e){subscription.cancel();complete.completeExceptionally(e);}}
                public void onError(Throwable error){complete.completeExceptionally(error);}public void onComplete(){complete.complete(null);}
            });
            try{complete.get();}catch(ExecutionException e){throw new IOException("Request body failed",e.getCause());}
        }
    }
    static final class Response<T> implements HttpResponse<T> {
        final int status; final HttpRequest request; final HttpHeaders headers; final T body; final SSLSession session; final URI uri; final HttpResponse<T> previous;
        Response(int status,HttpRequest request,HttpHeaders headers,T body,SSLSession session,URI uri,HttpResponse<T> previous){this.status=status;this.request=request;this.headers=headers;this.body=body;this.session=session;this.uri=uri;this.previous=previous;}
        public int statusCode(){return status;} public HttpRequest request(){return request;} public Optional<HttpResponse<T>> previousResponse(){return Optional.ofNullable(previous);}
        public HttpHeaders headers(){return headers;} public T body(){return body;} public Optional<SSLSession> sslSession(){return Optional.ofNullable(session);}
        public URI uri(){return uri;} public Version version(){return Version.HTTP_1_1;}
    }
    static final class Demand implements Flow.Subscription {
        long count;boolean cancelled;final InputStream input; final java.util.function.Consumer<Throwable> error;
        Demand(InputStream input,java.util.function.Consumer<Throwable> error){this.input=input;this.error=error;}
        public synchronized void request(long n){if(cancelled)return;if(n<=0){cancel();error.accept(new IllegalArgumentException("nonpositive demand"));return;}count=count>Long.MAX_VALUE-n?Long.MAX_VALUE:count+n;notifyAll();}
        public synchronized void cancel(){cancelled=true;notifyAll();try{input.close();}catch(IOException ignored){}}
        synchronized boolean await()throws InterruptedException{while(count==0&&!cancelled)wait();if(cancelled)return false;if(count!=Long.MAX_VALUE)count--;return true;}
    }
    static final class ConfiguredSockets extends SSLSocketFactory {
        final SSLSocketFactory delegate;final SSLParameters parameters; volatile SSLSocket socket;
        ConfiguredSockets(SSLSocketFactory delegate,SSLParameters parameters){this.delegate=delegate;this.parameters=parameters;}
        private Socket configure(Socket socket){if(socket instanceof SSLSocket){this.socket=(SSLSocket)socket;if(parameters!=null)this.socket.setSSLParameters(copy(parameters));}return socket;}
        public String[] getDefaultCipherSuites(){return delegate.getDefaultCipherSuites();}public String[] getSupportedCipherSuites(){return delegate.getSupportedCipherSuites();}
        public Socket createSocket(Socket socket,String host,int port,boolean close)throws IOException{return configure(delegate.createSocket(socket,host,port,close));}
        public Socket createSocket(String host,int port)throws IOException{return configure(delegate.createSocket(host,port));}
        public Socket createSocket(String host,int port,InetAddress local,int localPort)throws IOException{return configure(delegate.createSocket(host,port,local,localPort));}
        public Socket createSocket(InetAddress host,int port)throws IOException{return configure(delegate.createSocket(host,port));}
        public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort)throws IOException{return configure(delegate.createSocket(host,port,local,localPort));}
    }
}
