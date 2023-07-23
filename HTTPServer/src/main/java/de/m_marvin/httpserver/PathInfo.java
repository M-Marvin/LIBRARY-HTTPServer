package de.m_marvin.httpserver;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class PathInfo {
	
	public String path;
	public Map<String, String> query;
	public String fragment;
	
	public PathInfo(String resourcePath) {
		this.path = resolvePercentageCodes(resourcePath.split("\\?")[0].split("#")[0]);
		this.query = new HashMap<>();
		String as = resourcePath.substring(this.path.length());
		if (as.contains("?")) {
			String[] queryStrings = as.split("\\?")[1].split("#")[0].split("&");
			for (String q : queryStrings) {
				String[] a = q.split("=");
				this.query.put(resolvePercentageCodes(a[0]), a.length == 2 ? resolvePercentageCodes(a[1]) : "");
			}
		}
		if (as.contains("#")) {
			this.fragment = resolvePercentageCodes(as.split("#")[1].split("\\?")[0]);
		}
		if (this.path.equals("/")) this.path = "/index.html";
	}
	
	public static String resolvePercentageCodes(String encodedString) {
		try {
			return URLDecoder.decode(encodedString, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			System.err.println("Failed to encode URL!");
			e.printStackTrace();
			return "";
		}
	}
	
	public Map<String, String> getQuery() {
		return query;
	}
	
	public String getFragment() {
		return fragment;
	}
	
	public String getPath() {
		return path;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getPath());
		if (!this.getQuery().isEmpty()) {
			sb.append("?");
			int i = 0;
			for (Entry<String, String> e : this.getQuery().entrySet()) {
				if (i++ > 0) sb.append("&");
				sb.append(e.getKey()).append("=").append(e.getValue());
			}
		}
		if (!this.getFragment().isEmpty()) {
			sb.append("#").append(this.getFragment());
		}
		return sb.toString();
	}
	
}
