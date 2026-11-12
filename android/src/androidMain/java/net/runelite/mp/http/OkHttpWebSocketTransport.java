package net.runelite.mp.http;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
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
 * answers pings itself and exposes no ping/pong API, so {@link Connection#ping} and
 * {@link Connection#pong} report {@code false} and the shadow fails those sends.
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
			byte[] bytes = new byte[message.remaining()];
			message.get(bytes);
			if (!socket.send(ByteString.of(bytes)))
			{
				throw new IOException("WebSocket closing or its send queue is full");
			}
		}

		@Override
		public boolean ping(ByteBuffer payload)
		{
			return false;
		}

		@Override
		public boolean pong(ByteBuffer payload)
		{
			return false;
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
}
