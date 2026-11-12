package net.runelite.mp.http;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import net.runelite.awt.WebSocketTransport;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * The okhttp3 wire under the shadow {@code java.net.http.WebSocket}; android-awt loads it by
 * name ({@link WebSocketTransport#ANDROID_TRANSPORT}, kept in proguard-rules.pro). okhttp
 * answers pings itself and has no public ping/pong API, while the JDK WebSocket sends both and
 * reports both to its listener, so {@link Internals} reaches the frame reader and writer
 * okhttp keeps package-private.
 */
public final class OkHttpWebSocketTransport implements WebSocketTransport
{
	// One dispatcher and connection pool for every socket; read timeout 0 because a WebSocket
	// may sit idle indefinitely, as on the JDK client.
	private static final OkHttpClient BASE = new OkHttpClient.Builder()
		.readTimeout(0, TimeUnit.MILLISECONDS)
		.build();

	@Override
	@SuppressWarnings("deprecation")
	public Connection open(URI uri, Map<String, List<String>> headers, List<String> subprotocols,
		long connectTimeoutMillis, SSLSocketFactory ssl, ProxySelector proxy, Events events)
	{
		OkHttpClient.Builder client = BASE.newBuilder();
		if (connectTimeoutMillis > 0)
		{
			client.connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS);
		}
		if (proxy != null)
		{
			client.proxySelector(proxy);
		}
		if (ssl != null)
		{
			// The trust manager is not recoverable from an SSLContext; okhttp extracts it reflectively.
			client.sslSocketFactory(ssl);
		}
		Request.Builder request = new Request.Builder().url(uri.toString());
		for (Map.Entry<String, List<String>> header : headers.entrySet())
		{
			for (String value : header.getValue())
			{
				request.addHeader(header.getKey(), value);
			}
		}
		if (!subprotocols.isEmpty())
		{
			request.header("Sec-WebSocket-Protocol", String.join(", ", subprotocols));
		}
		OkConnection connection = new OkConnection();
		connection.socket = client.build().newWebSocket(request.build(), new WebSocketListener()
		{
			private volatile boolean opened;

			@Override
			public void onOpen(WebSocket webSocket, Response response)
			{
				opened = true;
				connection.socket = webSocket;
				// okhttp calls onOpen after creating the frame reader and before reading the
				// first frame, on the reader thread, so no ping can slip past the hook.
				Internals.reportControlFrames(webSocket, events);
				events.opened(connection, response.header("Sec-WebSocket-Protocol"));
			}

			@Override
			public void onMessage(WebSocket webSocket, String text)
			{
				events.text(text);
			}

			@Override
			public void onMessage(WebSocket webSocket, ByteString bytes)
			{
				events.binary(ByteBuffer.wrap(bytes.toByteArray()));
			}

			@Override
			public void onClosing(WebSocket webSocket, int code, String reason)
			{
				events.closing(code, reason);
			}

			@Override
			public void onFailure(WebSocket webSocket, Throwable error, Response response)
			{
				if (opened || response == null)
				{
					events.failed(error, 0, null);
				}
				else
				{
					events.failed(error, response.code(), response.headers().toMultimap());
				}
			}
		});
		return connection;
	}

	private static byte[] copy(ByteBuffer buffer)
	{
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		return bytes;
	}

	private static final class OkConnection implements Connection
	{
		volatile WebSocket socket;

		@Override
		public void text(String message) throws IOException
		{
			if (!socket.send(message))
			{
				throw new IOException("WebSocket closing or its send queue is full");
			}
		}

		@Override
		public void binary(ByteBuffer message) throws IOException
		{
			if (!socket.send(ByteString.of(copy(message))))
			{
				throw new IOException("WebSocket closing or its send queue is full");
			}
		}

		@Override
		public boolean ping(ByteBuffer payload) throws IOException
		{
			return Internals.ping(socket, ByteString.of(copy(payload)));
		}

		@Override
		public boolean pong(ByteBuffer payload) throws IOException
		{
			return Internals.pong(socket, ByteString.of(copy(payload)));
		}

		@Override
		public void close(int code, String reason)
		{
			socket.close(code, reason.isEmpty() ? null : reason);
		}

		@Override
		public void abort()
		{
			WebSocket current = socket;
			if (current != null)
			{
				current.cancel();
			}
		}
	}

	/**
	 * The okhttp3.internal.ws members behind ping and pong: {@code RealWebSocket.pong(ByteString)},
	 * its frame writer and the single-thread executor every frame write runs on, and the frame
	 * reader's callback. okhttp3.** is kept with its members in proguard-rules.pro; fields are
	 * found by type so a rename inside okhttp does not break them. When any is missing the
	 * transport reports ping and pong unsupported and the shadow fails those sends.
	 */
	private static final class Internals
	{
		private static final Internals INSTANCE = resolve();

		final Class<?> callbackType;
		final Field reader;
		final Field callback;
		final Field writer;
		final Field executor;
		final Method pong;
		final Method writePing;

		private Internals(Class<?> realWebSocket, Class<?> frameReader, Class<?> frameWriter, Class<?> callbackType)
			throws ReflectiveOperationException
		{
			this.callbackType = callbackType;
			reader = field(realWebSocket, frameReader);
			writer = field(realWebSocket, frameWriter);
			executor = field(realWebSocket, Executor.class);
			callback = field(frameReader, callbackType);
			pong = realWebSocket.getDeclaredMethod("pong", ByteString.class);
			pong.setAccessible(true);
			writePing = frameWriter.getDeclaredMethod("writePing", ByteString.class);
			writePing.setAccessible(true);
		}

		private static Internals resolve()
		{
			try
			{
				String ws = "okhttp3.internal.ws.";
				return new Internals(Class.forName(ws + "RealWebSocket"), Class.forName(ws + "WebSocketReader"),
					Class.forName(ws + "WebSocketWriter"), Class.forName(ws + "WebSocketReader$FrameCallback"));
			}
			catch (ReflectiveOperationException | RuntimeException | LinkageError missing)
			{
				return null;
			}
		}

		private static Field field(Class<?> owner, Class<?> type) throws NoSuchFieldException
		{
			for (Field field : owner.getDeclaredFields())
			{
				if (!Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType()))
				{
					field.setAccessible(true);
					return field;
				}
			}
			throw new NoSuchFieldException(owner.getName() + " has no " + type.getName() + " field");
		}

		/**
		 * Puts a proxy in front of okhttp's own frame callback so received pings and pongs reach
		 * the listener too; okhttp still answers the ping before it is reported, as the
		 * {@link Events#ping} contract asks.
		 */
		static void reportControlFrames(WebSocket socket, Events events)
		{
			Internals in = INSTANCE;
			if (in == null)
			{
				return;
			}
			try
			{
				Object frameReader = in.reader.get(socket);
				if (frameReader == null)
				{
					return;
				}
				Object original = in.callback.get(frameReader);
				Object hook = Proxy.newProxyInstance(in.callbackType.getClassLoader(), new Class<?>[]{in.callbackType},
					(proxy, method, args) ->
					{
						Object result;
						try
						{
							method.setAccessible(true);
							result = method.invoke(original, args);
						}
						catch (InvocationTargetException thrown)
						{
							throw thrown.getCause();
						}
						if (args != null && args.length == 1 && args[0] instanceof ByteString)
						{
							ByteBuffer payload = ByteBuffer.wrap(((ByteString) args[0]).toByteArray());
							if ("onReadPing".equals(method.getName()))
							{
								events.ping(payload);
							}
							else if ("onReadPong".equals(method.getName()))
							{
								events.pong(payload);
							}
						}
						return result;
					});
				in.callback.set(frameReader, hook);
			}
			catch (ReflectiveOperationException | RuntimeException failed)
			{
				// okhttp keeps answering pings itself; the listener only misses onPing/onPong.
			}
		}

		static boolean ping(WebSocket socket, ByteString payload) throws IOException
		{
			Internals in = INSTANCE;
			if (in == null)
			{
				return false;
			}
			Object frameWriter;
			Executor writes;
			try
			{
				frameWriter = in.writer.get(socket);
				writes = (Executor) in.executor.get(socket);
			}
			catch (IllegalAccessException | RuntimeException failed)
			{
				return false;
			}
			if (frameWriter == null || writes == null)
			{
				throw new IOException("WebSocket is not open");
			}
			try
			{
				// okhttp writes every frame from this executor; writing here keeps the ping
				// from interleaving with a data frame, as okhttp's own interval pings do.
				writes.execute(() ->
				{
					try
					{
						in.writePing.invoke(frameWriter, payload);
					}
					catch (ReflectiveOperationException failed)
					{
						// A broken writer means a broken socket: cancel it so the reader
						// fails and the listener gets onError, as okhttp does for its pings.
						socket.cancel();
					}
				});
			}
			catch (RejectedExecutionException closed)
			{
				throw new IOException("WebSocket closed", closed);
			}
			return true;
		}

		static boolean pong(WebSocket socket, ByteString payload) throws IOException
		{
			Internals in = INSTANCE;
			if (in == null)
			{
				return false;
			}
			Object queued;
			try
			{
				queued = in.pong.invoke(socket, payload);
			}
			catch (IllegalAccessException failed)
			{
				return false;
			}
			catch (InvocationTargetException failed)
			{
				throw new IOException("WebSocket pong failed", failed.getCause());
			}
			if (!Boolean.TRUE.equals(queued))
			{
				throw new IOException("WebSocket closing or failed");
			}
			return true;
		}
	}
}
