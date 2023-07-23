package de.m_marvin.httpserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;

public class Test {
	
	public static final String HTML_FOLDER = "/web";
	
	public static void main(String[] args) {
		
		try {
			HttpServer server = new HttpServer(new ServerSocket(80));
			server.setGetHandler(page -> {
				InputStream pageSource = Test.class.getResourceAsStream(HTML_FOLDER + page.getPath());
				return new ResponseInfo(pageSource == null ? HttpCode.NOT_FOUND : HttpCode.OK, pageSource == null ? "Not found!" : "OK", pageSource);
			});
			server.open();
			
			while (true);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
}
