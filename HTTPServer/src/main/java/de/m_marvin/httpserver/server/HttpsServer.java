package de.m_marvin.httpserver.server;

import java.io.File;
import java.io.IOException;

import javax.net.ssl.SSLServerSocketFactory;

public class HttpsServer extends HttpServer {
	
	public HttpsServer(int port) {
		super(port);
	}
	
	public HttpsServer(int port, int receptionTimeout) {
		super(port, receptionTimeout);
	}
	
	public HttpsServer(int port, File keyStoreFile, String keyStorePassword) {
		super(port);
		setKeystoreStore(keyStoreFile, keyStorePassword);
	}
	
	public HttpsServer(int port, int receptionTimeout, File keyStoreFile, String keyStorePassword) {
		super(port, receptionTimeout);
		setKeystoreStore(keyStoreFile, keyStorePassword);
	}
	
	protected static void setKeystoreStore(File keystoreStoreFile, String password) {
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
