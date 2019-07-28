package org.onvif.client;


import de.onvif.discovery.OnvifDiscovery;
import org.apache.cxf.common.logging.LogUtils;

import java.net.URL;
import java.util.Collection;
import java.util.logging.Logger;


public class DiscoveryTest {
	private static final Logger LOG = LogUtils.getL7dLogger(DiscoveryTest.class);

	public static void main(String[] args) {
		Collection<URL> urls = OnvifDiscovery.discoverOnvifURLs();
		for (URL u : urls) {
			LOG.info(u.toString());
		}
	}
}

