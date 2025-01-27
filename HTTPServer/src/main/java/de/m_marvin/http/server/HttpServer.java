package de.m_marvin.http.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import de.m_marvin.http.HttpCode;
import de.m_marvin.http.HttpRequest;
import de.m_marvin.http.PathInfo;
import de.m_marvin.http.ResponseInfo;
import de.m_marvin.simplelogging.Log;

public class HttpServer {
	
	public static final int DEFAULT_RECEPTION_TIMEOUT = 1500;
	
	protected final int port;
	protected final int receptionTimeout;
	protected ServerSocket serverSocket;
	protected Thread handleThread;
	
	public HttpServer(int port) {
		this(port, DEFAULT_RECEPTION_TIMEOUT);
	}
	
	public HttpServer(int port, int receptionTimeout) {
		this.port = port;
		this.receptionTimeout = receptionTimeout;
	}
	
	public void open() throws IOException {
		this.serverSocket = new ServerSocket(this.port);
		this.handleThread = new Thread(this::handleRequests, "HTTP handler");
		this.handleThread.setDaemon(true);
		this.handleThread.start();
	}
	
	public void close() throws IOException {
		this.serverSocket.close();
	}
	
	protected void handleRequests() {
		while (!this.serverSocket.isClosed()) {
			try {
				Socket clientSocket = this.serverSocket.accept();
				Thread clientHandlerThread = new Thread(() -> handleClient(clientSocket), "Handler-Thread");
				clientHandlerThread.setDaemon(true);
				clientHandlerThread.start();
			} catch (IOException e) {
				if (!this.serverSocket.isClosed())
					Log.defaultLogger().error("IOException while accepting request!", e);
			}
		}
	}
	
	protected void handleClient(Socket currentSocket) {
		try {
			currentSocket.setSoTimeout(this.receptionTimeout);
			String requestHeader = readPackageHeader(currentSocket);
			ResponseInfo response = handleMessage(requestHeader);
			if (response != null) {
				String responseHeader = makeMessage(response.getResponseCode(), response.getResponseMessage(), response.getAttributes());
				writePackageHeader(currentSocket, responseHeader);
				if (response.getContentSource().isPresent()) {
					try {
						response.getContentSource().get().transferTo(currentSocket.getOutputStream());
						response.getContentSource().get().close();
					} catch (IOException e) {
						throw new IOException("Unable to transfer all payload bytes!", e);
					}
				}
			}
			// Prevent the socket from being closed if the application requests it.
			// From this point onward, all control over this socket is transfered to the application.
			// No further attempts to close, send or write to/from this socket will be made by the HTTP server!
			if (!response.freeSocket(currentSocket)) currentSocket = null;
		} catch (SocketTimeoutException e) {
			try {
				writePackageHeader(currentSocket, makeMessage(HttpCode.BAD_REQUEST, "Reception Timeout", new HashMap<>()));
			} catch (IOException e1) {
				Log.defaultLogger().error("Failed to send timeout response!", e1);
			}
		} catch (SocketException e) {
			Log.defaultLogger().error("SocketException while handeling ServerSocket!", e);
		} catch (IOException e) {
			Log.defaultLogger().error("IOException on socket occured!", e);
		} finally {
			try {
				if (currentSocket != null) currentSocket.close();
			} catch (IOException e) {
				Log.defaultLogger().error("Could not close ServerSocket!", e);
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
	
	protected ResponseInfo handleMessage(String httpMessage) throws IOException {
		
		String[] messageLines = httpMessage.split("\\R");
		String[] headerLine = messageLines[0].split(" ");
		
		HttpRequest requestType = HttpRequest.fromName(headerLine[0]);
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
				Log.defaultLogger().error("Received invalid http header info line: " + messageLine);
			}
		}
		
		int payloadLen = getPayloadLength(attributes);
		
		ResponseInfo response = handleRequest(requestType, resourcePath, attributes, payloadLen, protocollTag);
		
		return response;
		
	}
	
	protected int getPayloadLength(Map<String, String> additionalInfo) throws IOException {
		if (additionalInfo.containsKey("Content-Length")) {
			try {
				return Integer.parseInt(additionalInfo.get("Content-Length"));
			} catch (NumberFormatException e) {
				Log.defaultLogger().error("Received invalid argument for Content-Length: " + additionalInfo.get("Content-Length"));
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
	
	protected ResponseInfo handleRequest(HttpRequest requestType, PathInfo resourcePath, Map<String, String> attributes, int payloadLen, String protocollTag) {
		switch (requestType) {
		case GET:
			return handleGet(resourcePath, attributes, false);
		case HEADER:
			return handleGet(resourcePath, attributes, true);
		case PUT:
			return handlePut(resourcePath, attributes, payloadLen);
		case POST:
			return handlePost(resourcePath, attributes, payloadLen);
		case DELETE:
			return handleDelete(resourcePath, attributes);
		default:
			Log.defaultLogger().error("Received invalid HTTP package!");
			return new ResponseInfo(HttpCode.BAD_REQUEST, "Invalid Method", null);
		}
	}
	
	@FunctionalInterface
	public static interface GetRequestHandler {
		public ResponseInfo handleRequest(PathInfo path, Map<String, String> attributes);
	}

	@FunctionalInterface
	public static interface PutRequestHandler {
		public ResponseInfo handleRequest(PathInfo path, Map<String, String> attributes, int contentLength);
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
	
	public ResponseInfo handlePost(PathInfo resourcePath, Map<String, String> attributes, int contentLength) {
		if (this.postHandler == null) return new ResponseInfo(HttpCode.NOT_IMPLEMENTED, "No Handler", null);
		return this.postHandler.handleRequest(resourcePath, attributes, contentLength);
	}
	
	public ResponseInfo handlePut(PathInfo resourcePath, Map<String, String> attributes, int contentLength) {
		if (this.putHandler == null) return new ResponseInfo(HttpCode.NOT_IMPLEMENTED, "No Handler", null);
		 return this.putHandler.handleRequest(resourcePath, attributes, contentLength);
	}
	
	public ResponseInfo handleDelete(PathInfo resourcePath, Map<String, String> attributes) {
		if (this.deleteHandler == null) return new ResponseInfo(HttpCode.NOT_IMPLEMENTED, "No Handler", null);
		return this.deleteHandler.handleRequest(resourcePath, attributes);
	}
	
}
