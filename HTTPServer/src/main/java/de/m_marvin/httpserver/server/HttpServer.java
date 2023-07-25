package de.m_marvin.httpserver.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import de.m_marvin.httpserver.HttpCode;
import de.m_marvin.httpserver.HttpRequest;
import de.m_marvin.httpserver.PathInfo;
import de.m_marvin.httpserver.ResponseInfo;

public class HttpServer {
	
	public static final int DEFAULT_RECEPTION_TIMEOUT = 1500;
	
	protected final int port;
	protected final int receptionTimeout;
	protected ServerSocket serverSocket;
	protected Thread handleThread;
	protected Socket currentSocket;
	
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
				if (this.currentSocket == null || this.currentSocket.isClosed()) {
					this.currentSocket = this.serverSocket.accept();
					this.currentSocket.setSoTimeout(this.receptionTimeout);
				}
				try {
					String httpMessage = readPackageHeader();
					byte[] responseMessage = handleMessage(httpMessage);
					if (responseMessage != null) writePackage(responseMessage);
				} catch (SocketTimeoutException e) {
					System.err.println("Connection timeout!");
					writePackage(makeMessage(HttpCode.BAD_REQUEST, "RECEPTION TIMEOUT", new HashMap<>(), Optional.empty()));
				} catch (Exception e) {
					System.err.println("Exception while handeling HTTP request!");
					e.printStackTrace();
				}
				this.currentSocket.close();
			} catch (Exception e) {
				System.err.println("Exception while handeling ServerSocket!");
				e.printStackTrace();
				if (!this.serverSocket.isClosed()) {
					try {
						this.currentSocket.close();
					} catch (IOException e1) {
						System.err.println("Could not close ServerSocket!");
						e1.printStackTrace();
					}
				}
			}
		}
	}
	
	protected String readPackageHeader() throws IOException {
		InputStream reader = this.currentSocket.getInputStream();
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
	
	protected byte[] readPackageContent(int bytes) throws IOException {
		byte[] contentBytes = new byte[bytes];
		this.currentSocket.getInputStream().read(contentBytes);
		return contentBytes;
	}
	
	protected void writePackage(byte[] httpMessage) throws IOException {
		this.currentSocket.getOutputStream().write(httpMessage);
	}
	
	protected byte[] handleMessage(String httpMessage) throws IOException {
		
//		System.out.println("##########################");
//		System.out.println(httpMessage);
		
		String[] messageLines = httpMessage.split("\\R");
		String[] headerLine = messageLines[0].split(" ");
		
		HttpRequest requestType = HttpRequest.fromName(headerLine[0]);
		PathInfo resourcePath = new PathInfo(headerLine[1]);
		String protocollTag = headerLine[2];
		
		Map<String, String> additionalInfo = new HashMap<>();
		int i;
		for (i = 1; i < messageLines.length; i++) {
			String messageLine = messageLines[i];
			if (messageLine.isEmpty() || (messageLine.length() == 1 && messageLine.endsWith("\r"))) break;
			String[] infoLine = messageLine.split(": ");
			if (infoLine.length == 2) {
				additionalInfo.put(infoLine[0], infoLine[1]);
			} else {
				System.err.println("Received invalid http header info line: " + messageLine);
			}
		}
		
		Optional<byte[]> content = handleAdditionalInfo(additionalInfo);
		
		byte[] response = handleRequest(requestType, resourcePath, protocollTag, content);
		
		return response;
		
	}
	
	protected Optional<byte[]> handleAdditionalInfo(Map<String, String> additionalInfo) throws IOException {
		Optional<byte[]> content = Optional.empty();
		
		if (additionalInfo.containsKey("Content-Length")) {
			try {
				int contentLength = Integer.parseInt(additionalInfo.get("Content-Length"));
				content = Optional.of(readPackageContent(contentLength));
			} catch (NumberFormatException e) {
				System.err.println("Received invalid argument for Content-Length: " + additionalInfo.get("Content-Length"));
			}
		}
		
		return content;
	}
	
	protected byte[] makeMessage(HttpCode code, String info, Map<String, String> additionalInfo, Optional<InputStream> content) {
		StringBuilder messageBuilder = new StringBuilder();
		messageBuilder.append("HTTP/1.1 ").append(code.getCode()).append(" ").append(info).append("\r\n");
		for (String key : additionalInfo.keySet()) {
			messageBuilder.append(key).append(": ").append(additionalInfo.get(key)).append("\r\n");
		}
		messageBuilder.append("\r\n");
		
		byte[] headerBytes = messageBuilder.toString().getBytes();
		
		if (content.isPresent()) {
			byte[] contentBytes;
			try {
				contentBytes = content.get().readAllBytes();
				content.get().close();
			} catch (IOException e) {
				contentBytes = e.getMessage().getBytes();
			}
			byte[] endofmessageBytes = new String("\r\n").getBytes();
			byte[] messageBytes = new byte[headerBytes.length + contentBytes.length + endofmessageBytes.length];
			System.arraycopy(headerBytes, 0, messageBytes, 0, headerBytes.length);
			System.arraycopy(contentBytes, 0, messageBytes, headerBytes.length, contentBytes.length);
			System.arraycopy(endofmessageBytes, 0, messageBytes, headerBytes.length + contentBytes.length, endofmessageBytes.length);
			return messageBytes;
		} else {
			return headerBytes;
		}
	}
	
	protected byte[] handleRequest(HttpRequest requestType, PathInfo resourcePath, String protocollTag, Optional<byte[]> content) {
		
		try {
			switch (requestType) {
			case GET:
				return handleGet(resourcePath, false);
			case HEADER:
				return handleGet(resourcePath, true);
			case PUT:
				return handlePut(resourcePath, content.get());
			case POST:
				return handlePost(resourcePath, content.get());
			case DELETE:
				return handleDelete(resourcePath);
			default:
				System.err.println("Received invalid HTTP package!");
				return makeMessage(HttpCode.NOT_SUPPORTED, "Invalid request", new HashMap<>(), Optional.empty());
			}
		} catch (NoSuchElementException e) {
			System.err.println("Received " + requestType.toString() + " without any data!");
			return makeMessage(HttpCode.BAD_REQUEST, "Missing data-body in message", new HashMap<>(), Optional.empty());
		}
		
	}
	
	protected byte[] makeNoHandlerMessage() {
		System.err.println("Could not handle request!");
		return makeMessage(HttpCode.NOT_SUPPORTED, "Not supported", new HashMap<>(), Optional.empty());
	}
	
	protected Function<PathInfo, ResponseInfo> getHandler;
	
	public void setGetHandler(Function<PathInfo, ResponseInfo> getHandler) {
		this.getHandler = getHandler;
	}

	protected BiFunction<PathInfo, byte[], ResponseInfo> postHandler;
	
	public void setPostHandler(BiFunction<PathInfo, byte[], ResponseInfo>  postHandler) {
		this.postHandler = postHandler;
	}

	protected Function<PathInfo, ResponseInfo> deleteHandler;
	
	public void setDeleteHandler(Function<PathInfo, ResponseInfo> deleteHandler) {
		this.deleteHandler = deleteHandler;
	}

	protected BiFunction<PathInfo, byte[], ResponseInfo> putHandler;
	
	public void setPutHandler(BiFunction<PathInfo, byte[], ResponseInfo>  putHandler) {
		this.putHandler = putHandler;
	}
	
	public byte[] handleGet(PathInfo resourcePath, boolean onlyHeader) {
		if (this.getHandler == null) return makeNoHandlerMessage();
		ResponseInfo response = this.getHandler.apply(resourcePath);
		return makeMessage(response.getResponseCode(), response.getResponseMessage(), response.getAdditionalInfo(), onlyHeader ? Optional.empty() : response.getContentSource());
	}
	
	public byte[] handlePost(PathInfo resourcePath, byte[] content) {
		if (this.postHandler == null) return makeNoHandlerMessage();
		ResponseInfo response = this.postHandler.apply(resourcePath, content);
		return makeMessage(response.getResponseCode(), response.getResponseMessage(), response.getAdditionalInfo(), Optional.empty());
	}
	
	public byte[] handlePut(PathInfo resourcePath, byte[] content) {
		if (this.putHandler == null) return makeNoHandlerMessage();
		ResponseInfo response = this.putHandler.apply(resourcePath, content);
		return makeMessage(response.getResponseCode(), response.getResponseMessage(), response.getAdditionalInfo(), response.getContentSource());
	}
	
	public byte[] handleDelete(PathInfo resourcePath) {
		if (this.deleteHandler == null) return makeNoHandlerMessage();
		ResponseInfo response = this.deleteHandler.apply(resourcePath);
		return makeMessage(response.getResponseCode(), response.getResponseMessage(), response.getAdditionalInfo(), response.getContentSource());
	}
	
}
