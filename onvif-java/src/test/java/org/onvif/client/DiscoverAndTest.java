package org.onvif.client;

import de.onvif.discovery.OnvifDiscovery;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;
import org.apache.cxf.common.logging.LogUtils;

/**
 * Class calls OnvifDiscovery and for each device URL found, calls TestDevice
 * This assumes all onvif devices on your network use the same username and password.
 * @author Brad Lowe
 */
public class DiscoverAndTest {
  private static final Logger LOG = LogUtils.getL7dLogger(TestDevice.class);

  public static String discoverAndTest(String user, String password) {
    String sep = "\n";
    StringBuffer out = new StringBuffer();

    Collection<URL> urls = OnvifDiscovery.discoverOnvifURLs();
    for (URL u : urls) {
      out.append("Discovered URL:" + u.toString() + sep);
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
        // This is a bit of a hack. When a camera is password protected (it should be!)
        // and the password is not provided or wrong, a "Unable to Send Message" exception
        // is thrown. This is not clear-- buried in the stack track is the real cause.
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String trace = sw.getBuffer().toString();
        if (trace.contains("Unauthorized")) results.add(u + ":Unauthorized:");
        else results.add(u + ":" + trace);
      }
    }
    out.append("RESULTS: " + sep);
    for (String s : results) out.append(s + sep);
    out.append("cameras found:" + urls.size() + " good=" + good + ", bad=" + bad + sep);
    return out.toString();
  }

  public static void main(String[] args) {
    // get user and password.. we will ignore device host
    OnvifCredentials creds = GetTestDevice.getOnvifCredentials(args);
    if (creds.getPassword().isEmpty()) LOG.info("Warning: No password for discover and test...");
    LOG.info(discoverAndTest(creds.getUser(), creds.getPassword()));
  }
}
