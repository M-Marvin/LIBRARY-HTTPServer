package de.m_marvin.httpserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import de.m_marvin.http.HttpCode;
import de.m_marvin.http.server.HttpServer;
import de.m_marvin.http.server.ResponseInfo;
import de.m_marvin.websocket.WebSocket;
import de.m_marvin.websocket.WebSocketCode;
import de.m_marvin.websocket.WebSocketUtility;

public class Test {
	
	public static final String HTML_FOLDER = "/web";
	
	public static void main(String[] args) throws URISyntaxException, InterruptedException {
		
		File runDir = new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
		File certificateFile = new File(runDir, "../run/keystore.pfx");
		
		try {
			HttpServer server = new HttpServer(80);
			//HttpServer server = new HttpsServer(443, certificateFile, "password");
			server.setGetHandler((socket, path, attributes) -> {
				
				if (path.getPath().startsWith("/websock")) {
					
					ResponseInfo response = WebSocketUtility.verifyUpgradeHttpSocket(socket, attributes, "logs");
					
					if (response.getResponseCode() == HttpCode.SWITCHING_PROTOCOLS) {

						CompletableFuture.runAsync(() -> {
							
							try {
								
								WebSocket webSocket = new WebSocket(socket);
								String line = webSocket.readLine();
								System.out.println(line);
								String pong = new String(webSocket.sendPing("PING PING".getBytes()).orTimeout(1, TimeUnit.SECONDS).join());
								System.out.println(pong);
								webSocket.sendText("Hello World");
								webSocket.closeSocket(WebSocketCode.CLOSE_NORMALY, "Bye");
								
							} catch (IOException e) {
								e.printStackTrace();
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
						});
						
					}
					
					return response;
					
				}
				
				InputStream pageSource = Test.class.getResourceAsStream(HTML_FOLDER + path.getPath());
				return new ResponseInfo(pageSource == null ? HttpCode.NOT_FOUND : HttpCode.OK, pageSource == null ? "Not found!" : "OK", pageSource);
			});
			server.open();
			
			Thread.sleep(600000000);
			
			server.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
}
