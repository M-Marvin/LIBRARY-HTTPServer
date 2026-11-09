package de.m_marvin.http.server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import javax.net.ssl.SSLServerSocketFactory;

public class HttpsServer extends HttpServer {
	
	public static final int DEFAULT_HTTPS_PORT = 443;

	public HttpsServer() {
		this(DEFAULT_HTTPS_PORT);
	}
	
	public HttpsServer(int port) {
		this(port, DEFAULT_TIMEOUT);
	}
	
	public HttpsServer(int port, int timeout) {
		this(port, DEFAULT_BACKLOG, timeout);
	}
	
	public HttpsServer(int port, int backlog, int timeout) {
		this(port, null, backlog, timeout);
	}
	
	public HttpsServer(int port, InetAddress address, int backlog, int timeout) {
		this(port, address, backlog, timeout, ForkJoinPool.commonPool());
	}

	public HttpsServer(Executor threadpool) {
		this(DEFAULT_HTTPS_PORT, threadpool);
	}
	
	public HttpsServer(int port, Executor threadpool) {
		this(port, DEFAULT_TIMEOUT, threadpool);
	}
	
	public HttpsServer(int port, int timeout, Executor threadpool) {
		this(port, DEFAULT_BACKLOG, timeout, threadpool);
	}
	
	public HttpsServer(int port, int backlog, int timeout, Executor threadpool) {
		this(port, null, backlog, timeout, threadpool);
	}
	
	public HttpsServer(int port, InetAddress address, int backlog, int timeout, Executor threadpool) {
		super(port, address, backlog, timeout, threadpool);
	}
	
	public static void setKeystoreStore(File keystoreStoreFile, String password) {
		String keystore = keystoreStoreFile.toString().replace('\\', '/');
		System.setProperty("javax.net.ssl.keyStore", keystore);
		System.setProperty("javax.net.ssl.keyStorePassword", password);
	}
	
	 @Override
	public void open() throws IOException {
		this.serverSocket = SSLServerSocketFactory.getDefault().createServerSocket(this.port);
		this.handleThread = new Thread(this::handleRequests, "HTTP handler");
		this.handleThread.setDaemon(true);
		this.handleThread.start();
	}
	
}
