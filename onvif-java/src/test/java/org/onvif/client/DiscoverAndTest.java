package org.onvif.client;


import de.onvif.discovery.OnvifDiscovery;
import org.apache.cxf.common.logging.LogUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;
/* Class calls OnvifDiscovery and for each device URL found, calls TestDevice */

public class DiscoverAndTest {
	private static final Logger LOG = LogUtils.getL7dLogger(TestDevice.class);

	public static String discoverAndTest(String user, String password)
	{
		String out  ="";
		String sep = "\n";

		Collection<URL> urls = OnvifDiscovery.discoverOnvifURLs();
		for (URL u : urls) {
			out += ("Discovered URL:"+u.toString())+sep;
		}
		ArrayList<String> results = new ArrayList<>();

		int good = 0, bad = 0;

		for (URL u : urls) {

			try {
				String result = TestDevice.testCamera(u, user, password);
				LOG.info(u + "->" + result);
				good++;
				results.add(u.toString() + ":" + result);
			} catch (Throwable e) {
				bad++;
				LOG.severe("error:" + u + " " + e.toString());

				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				String trace = sw.getBuffer().toString();
				if (trace.contains("Unauthorized"))
					results.add(u + ":Unauthorized:");
				else
					results.add(u + ":" + trace);
			}
		}
		out += "RESULTS: "+sep;
		for (String s : results)
			out += s+sep;
		out +=("cameras found:" + urls.size() + " good=" + good + ", bad=" + bad)+sep;
		return out;
	}

	public static void main(String[] args) {
		OnvifCredentials creds = GetTestDevice.getOnvifCredentials(args);

		String user=null,pass;
		if (args.length==3) {
			user = args[1];
			pass = args[2];
		} else
		{
			user=System.getProperty("ONVIF_USER");
			pass=System.getProperty("ONVIF_PASSWORD");;
			if (user==null) user="";
			if (pass==null) pass="";
		}

		if (creds.getPassword().isEmpty()) LOG.info("Warning: No password for discover and test...");

		LOG.info(discoverAndTest(creds.getUser(),creds.getPassword()));
	}
}

