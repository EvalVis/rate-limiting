package com.evalvis.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ratelimiter")
public class RatelimiterProperties {

	private Forward forward = new Forward();
	private RateLimit rateLimit = new RateLimit();

	public Forward getForward() {
		return forward;
	}

	public void setForward(Forward forward) {
		this.forward = forward;
	}

	public RateLimit getRateLimit() {
		return rateLimit;
	}

	public void setRateLimit(RateLimit rateLimit) {
		this.rateLimit = rateLimit;
	}

	public static class Forward {

		private String host = "127.0.0.1";
		private int port = 8080;
		private String scheme = "http";

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public int getPort() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public String getScheme() {
			return scheme;
		}

		public void setScheme(String scheme) {
			this.scheme = scheme;
		}

		public String baseUrl() {
			return scheme + "://" + host + ":" + port;
		}

	}

	public static class RateLimit {

		private double capacity = 100;
		private double refillPerSecond = 10;

		public double getCapacity() {
			return capacity;
		}

		public void setCapacity(double capacity) {
			this.capacity = capacity;
		}

		public double getRefillPerSecond() {
			return refillPerSecond;
		}

		public void setRefillPerSecond(double refillPerSecond) {
			this.refillPerSecond = refillPerSecond;
		}

	}

}
