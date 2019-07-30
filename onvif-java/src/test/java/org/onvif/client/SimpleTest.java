package org.onvif.client;

import de.onvif.soap.OnvifDevice;
import org.apache.commons.io.FileUtils;
import org.onvif.ver10.media.wsdl.Media;
import org.onvif.ver10.schema.*;
import org.onvif.ver20.ptz.wsdl.PTZ;

import javax.xml.soap.SOAPException;
import java.io.File;
import java.io.FileInputStream;
import java.lang.Object;
import java.net.ConnectException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;


public class SimpleTest {

	// This test reads connection params from a properties file and take a
	// screenshot
	public static void main(String[] args) throws Exception {


		final Map<String, OnvifDevice> onvifCameras = new HashMap<>();
		final Map<String, String> onvifCamerasTokens = new HashMap<>();
		final String propFileRelativePath = "src/test/resources/onvif.properties";
		final Properties config = new Properties();
		final File f = new File(propFileRelativePath);
		if (!f.exists()) throw new Exception("fnf: " + f.getAbsolutePath());

		config.load(new FileInputStream(f));
		String firstCamId = null;
		for (Entry<Object, Object> entry : config.entrySet()) {
			String deviceName = (String) entry.getKey();
			String[] confStr = ((String) entry.getValue()).split(",");
			String deviceIp = confStr[0];
			String user = confStr[1];
			String password = confStr[2];
			// profileToken = "MediaProfile000"/"MediaProfile001";
			String profileToken = confStr[3];
			try {
				System.out.println("Connect to camera, please wait ...");
				OnvifDevice cam = new OnvifDevice(deviceIp, user, password);
				System.out.printf("Connected to device %s (%s)\n", cam.getDeviceInfo(), deviceName);
				onvifCameras.put(deviceName, cam);
				onvifCamerasTokens.put(deviceName, profileToken);
				if (firstCamId == null)
					firstCamId = deviceName;
			} catch (ConnectException | SOAPException e1) {
				e1.printStackTrace();
				System.err.println("No connection to device with ip " + deviceIp + ", please try again.");
				System.exit(0);
			}
		}
		if (firstCamId == null) {
			System.out.println("No ONVIF devices found");
			return;
		}
		// take the first OnvifDevice
		OnvifDevice firstCam = onvifCameras.get(firstCamId);
		String profileToken = onvifCamerasTokens.get(firstCamId);
		Media media = firstCam.getMedia();

		StreamSetup streamSetup = new StreamSetup();

		Transport t = new Transport();

		t.setProtocol(TransportProtocol.RTSP);
		streamSetup.setTransport(t);
		streamSetup.setStream(StreamType.RTP_UNICAST);


		MediaUri rtsp = media.getStreamUri(streamSetup, profileToken);
		System.out.println("rtspURL: " + rtsp + ": " + rtsp.getUri());


		Profile profile = media.getProfile(profileToken);


		// Example 1 - take a snapshot (the file gets deleted once the app ends..)
		MediaUri sceenshotUri = media.getSnapshotUri(profileToken);
		File tempFile = File.createTempFile("tmp", ".jpg");
		FileUtils.copyURLToFile(new URL(sceenshotUri.getUri()), tempFile);
		System.out.println("snapshot: " + tempFile.getAbsolutePath() + " length:" + tempFile.length());

		PTZ ptz = firstCam.getPtz();
		if (ptz != null) {
			List<PTZPreset> presets = ptz.getPresets(profileToken);
			if (presets != null && !presets.isEmpty()) {
				System.out.println("Found " + presets.size() + " presets");
			}
		}


	}

}