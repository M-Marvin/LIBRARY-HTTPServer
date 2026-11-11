package de.m_marvin.http.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;

import de.m_marvin.http.HttpCode;
import de.m_marvin.http.HttpRequest;
import de.m_marvin.http.PathInfo;
import de.m_marvin.http.ResponseInfo;
import de.m_marvin.simplelogging.Log;

public class HttpServer {

	public static final int DEFAULT_HTTP_PORT = 80;
	public static final int DEFAULT_TIMEOUT = 10000;
	public static final int DEFAULT_BACKLOG = 50;
	
	protected final int port;
	protected final int backlog;
	protected final InetAddress address;
	protected final int receptionTimeout;
	protected final Executor threadpool;
	protected ServerSocket serverSocket;
	protected Thread handleThread;

	public HttpServer() {
		this(DEFAULT_HTTP_PORT);
	}
	
	public HttpServer(int port) {
		this(port, DEFAULT_TIMEOUT);
	}
	
	public HttpServer(int port, int timeout) {
		this(port, DEFAULT_BACKLOG, timeout);
	}
	
	public HttpServer(int port, int backlog, int timeout) {
		this(port, null, backlog, timeout);
	}
	
	public HttpServer(int port, InetAddress address, int backlog, int timeout) {
		this(port, address, backlog, timeout, ForkJoinPool.commonPool());
	}

	public HttpServer(Executor threadpool) {
		this(DEFAULT_HTTP_PORT, threadpool);
	}
	
	public HttpServer(int port, Executor threadpool) {
		this(port, DEFAULT_TIMEOUT, threadpool);
	}
	
	public HttpServer(int port, int timeout, Executor threadpool) {
		this(port, DEFAULT_BACKLOG, timeout, threadpool);
	}
	
	public HttpServer(int port, int backlog, int timeout, Executor threadpool) {
		this(port, null, backlog, timeout, threadpool);
	}
	
	public HttpServer(int port, InetAddress address, int backlog, int timeout, Executor threadpool) {
		this.port = port;
		this.address = address;
		this.backlog = backlog;
		this.receptionTimeout = timeout;
		this.threadpool = threadpool;
	}
	
	public void open() throws IOException {
		if (this.serverSocket != null && !this.serverSocket.isClosed())
			return;
		this.serverSocket = new ServerSocket(this.port, this.backlog, this.address);
		this.handleThread = new Thread(this::handleRequests, "HTTP Request Handler");
		this.handleThread.setDaemon(true);
		this.handleThread.start();
	}
	
	public void close() throws IOException {
		if (this.serverSocket != null)
			this.serverSocket.close();
	}
	
	public boolean isOpen() {
		return !this.serverSocket.isClosed();
	}
	
	protected void handleRequests() {
		while (!this.serverSocket.isClosed()) {
			try {
				Socket clientSocket = this.serverSocket.accept();
				this.threadpool.execute(() -> handleClient(clientSocket));
			} catch (Throwable e) {
				if (!this.serverSocket.isClosed())
					Log.defaultLogger().warnt("HTTPServer", "Exception while accepting request!", e);
			}
		}
	}
	
	protected void handleClient(Socket currentSocket) {
		try {
			currentSocket.setSoTimeout(this.receptionTimeout);
			String requestHeader = readPackageHeader(currentSocket);
			ResponseInfo response = handleMessage(requestHeader, currentSocket.getInputStream());
			if (response != null) {
				String responseHeader = makeMessage(response.getResponseCode(), response.getResponseMessage(), response.getAttributes());
				writePackageHeader(currentSocket, responseHeader);
				if (response.getContentSource().isPresent()) {
					try {
						response.getContentSource().get().transferTo(currentSocket.getOutputStream());
						response.getContentSource().get().close();
					} catch (IOException e) {
						Log.defaultLogger().warnt("HTTPServer", "Unable to transfer all payload bytes!", e);
						return;
					}
				}
				// Prevent the socket from being closed if the application requests it.
				// From this point onward, all control over this socket is transfered to the application.
				// No further attempts to close, send or write to/from this socket will be made by the HTTP server!
				if ( !response.freeSocket(currentSocket)) currentSocket = null;
			}
		} catch (SocketTimeoutException e) {
			try {
				writePackageHeader(currentSocket, makeMessage(HttpCode.BAD_REQUEST, "Reception Timeout", new HashMap<>()));
			} catch (IOException e1) {
				Log.defaultLogger().warnt("HTTPServer", "Failed to send timeout response!", e1);
			}
		} catch (SocketException e) {
			Log.defaultLogger().warnt("HTTPServer", "SocketException while handeling ServerSocket!", e);
		} catch (IOException e) {
			Log.defaultLogger().warnt("HTTPServer", "IOException on socket occured!", e);
		} finally {
			try {
				if (currentSocket != null) currentSocket.close();
			} catch (IOException e) {
				Log.defaultLogger().warnt("HTTPServer", "Could not close ServerSocket!", e);
			}
		}
	}
	
	protected String readPackageHeader(Socket currentSocket) throws IOException {
		InputStream reader = currentSocket.getInputStream();
		StringBuilder messageBuilder = new StringBuilder();
		while (true) {
			StringBuilder lineBuffer = new StringBuilder();
			while (true) {
				char character = (char) reader.read();
				boolean isLineBreak = Pattern.matches("\\R", "" + character);
				if (!isLineBreak) {
					lineBuffer.append(character);
				} else {
					reader.read();
					break;
				}
			}
			String line = lineBuffer.toString();
			if (!line.isEmpty() && !(line.length() == 1 && line.contains("\\R") && messageBuilder.length() < 10000)) {
				messageBuilder.append(line).append("\r\n");
			} else {
				break;
			}
		}
		return messageBuilder.toString();
	}
	
	protected void writePackageHeader(Socket currentSocket, String header) throws IOException {
		currentSocket.getOutputStream().write(header.getBytes(StandardCharsets.UTF_8));
	}
	
	protected ResponseInfo handleMessage(String httpMessage, InputStream payloadStream) throws IOException {
		
		String[] messageLines = httpMessage.split("\\R");
		String[] headerLine = messageLines[0].split(" ");
		
		HttpRequest requestType = HttpRequest.fromName(headerLine[0]);
		
		if (requestType == null) {
			Log.defaultLogger().warnt("HTTPServer", "received invalid http request: %s (first 100 characters)", httpMessage.substring(0, Math.min(100, httpMessage.length())));
			return null;
		}
		
		PathInfo resourcePath = new PathInfo(headerLine[1]);
		String protocollTag = headerLine[2];
		
		Map<String, String> attributes = new LinkedHashMap<>();
		int i;
		for (i = 1; i < messageLines.length; i++) {
			String messageLine = messageLines[i];
			if (messageLine.isEmpty() || (messageLine.length() == 1 && messageLine.endsWith("\r"))) break;
			String[] infoLine = messageLine.split(": ");
			if (infoLine.length == 2) {
				attributes.put(infoLine[0], infoLine[1]);
			} else {
				Log.defaultLogger().warnt("HTTPServer", "Received invalid http header info line: " + messageLine);
			}
		}
		
		int payloadLen = getPayloadLength(attributes);
		
		ResponseInfo response = handleRequest(requestType, resourcePath, attributes, payloadLen, protocollTag, payloadStream);
		
		return response;
		
	}
	
	protected int getPayloadLength(Map<String, String> additionalInfo) throws IOException {
		if (additionalInfo.containsKey("Content-Length")) {
			try {
				return Integer.parseInt(additionalInfo.get("Content-Length"));
			} catch (NumberFormatException e) {
				Log.defaultLogger().warnt("HTTPServer", "Received invalid argument for Content-Length: " + additionalInfo.get("Content-Length"));
			}
		}
		return 0;
	}
	
	protected String makeMessage(HttpCode code, String info, Map<String, String> additionalInfo) {
		StringBuilder messageBuilder = new StringBuilder();
		messageBuilder.append("HTTP/1.1 ").append(code.code()).append(" ").append(info).append("\r\n");
		for (String key : additionalInfo.keySet()) {
			messageBuilder.append(key).append(": ").append(additionalInfo.get(key)).append("\r\n");
		}
		messageBuilder.append("\r\n");
		return messageBuilder.toString();
	}
	
	protected ResponseInfo handleRequest(HttpRequest requestType, PathInfo resourcePath, Map<String, String> attributes, int payloadLen, String protocollTag, InputStream payloadStream) {
		switch (requestType) {
		case GET:
			return handleGet(resourcePath, attributes, false);
		case HEADER:
			return handleGet(resourcePath, attributes, true);
		case PUT:
			return handlePut(resourcePath, attributes, payloadLen, payloadStream);
		case POST:
			return handlePost(resourcePath, attributes, payloadLen, payloadStream);
		case DELETE:
			return handleDelete(resourcePath, attributes);
		default:
			Log.defaultLogger().warnt("HTTPServer", "Received invalid HTTP package!");
			return new ResponseInfo(HttpCode.BAD_REQUEST, "Invalid Method", null);
		}
	}
	
	@FunctionalInterface
	public static interface GetRequestHandler {
		public ResponseInfo handleRequest(PathInfo path, Map<String, String> attributes);
	}

	@FunctionalInterface
	public static interface PutRequestHandler {
		public ResponseInfo handleRequest(PathInfo path, Map<String, String> attributes, int contentLength, InputStream contentStream);
	}

	@FunctionalInterface
	public static interface DelRequestHandler {
		public ResponseInfo handleRequest(PathInfo path, Map<String, String> attributes);
	}
	
	protected GetRequestHandler getHandler;
	
	public void setGetHandler(GetRequestHandler getHandler) {
		this.getHandler = getHandler;
	}

	protected PutRequestHandler postHandler;
	
	public void setPostHandler(PutRequestHandler postHandler) {
		this.postHandler = postHandler;
	}

	protected DelRequestHandler deleteHandler;
	
	public void setDeleteHandler(DelRequestHandler deleteHandler) {
		this.deleteHandler = deleteHandler;
	}

	protected PutRequestHandler putHandler;
	
	public void setPutHandler(PutRequestHandler putHandler) {
		this.putHandler = putHandler;
	}
	
	public ResponseInfo handleGet(PathInfo resourcePath, Map<String, String> attributes, boolean onlyHeader) {
		if (this.getHandler == null) return new ResponseInfo(HttpCode.NOT_IMPLEMENTED, "No Handler", null);
		return this.getHandler.handleRequest(resourcePath, attributes);
	}
	
	public ResponseInfo handlePost(PathInfo resourcePath, Map<String, String> attributes, int contentLength, InputStream contentStream) {
		if (this.postHandler == null) return new ResponseInfo(HttpCode.NOT_IMPLEMENTED, "No Handler", null);
		return this.postHandler.handleRequest(resourcePath, attributes, contentLength, contentStream);
	}
	
	public ResponseInfo handlePut(PathInfo resourcePath, Map<String, String> attributes, int contentLength, InputStream contentStream) {
		if (this.putHandler == null) return new ResponseInfo(HttpCode.NOT_IMPLEMENTED, "No Handler", null);
		 return this.putHandler.handleRequest(resourcePath, attributes, contentLength, contentStream);
	}
	
	public ResponseInfo handleDelete(PathInfo resourcePath, Map<String, String> attributes) {
		if (this.deleteHandler == null) return new ResponseInfo(HttpCode.NOT_IMPLEMENTED, "No Handler", null);
		return this.deleteHandler.handleRequest(resourcePath, attributes);
	}
	
}
