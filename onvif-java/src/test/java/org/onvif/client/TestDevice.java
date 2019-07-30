package org.onvif.client;

import de.onvif.beans.DeviceInfo;
import de.onvif.soap.OnvifDevice;
import de.onvif.utils.OnvifUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.onvif.ver10.device.wsdl.DeviceServiceCapabilities;
import org.onvif.ver10.device.wsdl.SystemCapabilities;
import org.onvif.ver10.events.wsdl.EventPortType;
import org.onvif.ver10.events.wsdl.GetEventProperties;
import org.onvif.ver10.events.wsdl.GetEventPropertiesResponse;
import org.onvif.ver10.media.wsdl.Media;
import org.onvif.ver10.schema.*;
import org.onvif.ver20.imaging.wsdl.ImagingPort;
import org.onvif.ver20.ptz.wsdl.Capabilities;
import org.onvif.ver20.ptz.wsdl.PTZ;
import org.w3c.dom.Element;

import javax.xml.soap.SOAPException;
import java.io.IOException;
import java.lang.Object;
import java.net.URL;
import java.util.List;
import java.util.logging.Logger;


public class TestDevice {
	private static final Logger LOG = LogUtils.getL7dLogger(TestDevice.class);

	public static String testCamera(OnvifCredentials creds) throws SOAPException, IOException {
		URL u = creds.getHost().startsWith("http") ? new URL(creds.getHost()) : new URL("http://" + creds.getHost());
		return testCamera(u, creds.getUser(), creds.getPassword());
	}

	static String sep = "\n";

	// This method returns information about an initialized OnvifDevice.
	// This could throw an uncaught SOAP or other error on some cameras...
	// Would accept Pull Requests on printing out additional information about devices.
	public static String inspect(OnvifDevice device) {
		String out = "";
		DeviceInfo info = device.getDeviceInfo();
		out += "DeviceInfo:" + info + sep;

		Media media = device.getMedia();

		media.getVideoSources();


		List<Profile> profiles = media.getProfiles();
		out += "Media Profiles: " + profiles.size() + sep;

		for (Profile profile : profiles) {
			String profileToken = profile.getToken();
			String rtsp = device.getRTSPURL(profileToken);
			out += "\tProfile: " + profile.getName() + " token=" + profile.getToken() + sep;
			out += "\t\trtsp: " + rtsp + sep;
			out += "\t\tsnapshot: " + device.getSnapshotUri(profileToken) + sep;
			out += "\t\tdetails:" + OnvifUtils.format(profile) + sep;
		}

		List<VideoSource> videoSources = media.getVideoSources();
		out += "VideoSources: " + videoSources.size() + sep;
		for (VideoSource v : videoSources)
			out += "\t" + OnvifUtils.format(v) + sep;
		List<AudioSource> audioSources = media.getAudioSources();
		out += "AudioSources: " + audioSources.size() + sep;
		for (AudioSource a : audioSources)
			out += "\t" + OnvifUtils.format(a) + sep;

		out += "hostName=" + device.getHostname() + sep;
		DeviceServiceCapabilities caps = device.getDevice().getServiceCapabilities();
		out += "getServiceCapabilities=" + OnvifUtils.format(caps) + sep;
		out += "getServiceCapabilities.getSystem=" + OnvifUtils.format(caps.getSystem()) + sep;

		ImagingPort imaging = device.getImaging();
		if (imaging != null && videoSources.size() > 0) {
			String token = videoSources.get(0).getToken();

			out += "Imaging:" + token + sep;
			try {
				org.onvif.ver20.imaging.wsdl.Capabilities image_caps = imaging.getServiceCapabilities();
				out += "\tgetServiceCapabilities=" + OnvifUtils.format(image_caps) + sep;

				if (token != null) {
					out += "\tgetImagingSettings=" + OnvifUtils.format(imaging.getImagingSettings(token)) + sep;
					out += "\tgetMoveOptions=" + OnvifUtils.format(imaging.getMoveOptions(token)) + sep;
					out += "\tgetStatus=" + OnvifUtils.format(imaging.getStatus(token)) + sep;
					out += "\tgetOptions=" + OnvifUtils.format(imaging.getOptions(token)) + sep;
				}
			} catch (Throwable th) {
				out += "error=" + th.getMessage() + sep;
			}
		}


		EventPortType events = device.getEvents();
		if (events != null) {
			out += "Events:" + sep;
			out += "\tgetServiceCapabilities=" + OnvifUtils.format(events.getServiceCapabilities()) + sep;


			GetEventProperties getEventProperties = new GetEventProperties();
			GetEventPropertiesResponse getEventPropertiesResp = events.getEventProperties(getEventProperties);
			getEventPropertiesResp.getMessageContentFilterDialect().forEach(x -> System.out.println(x));
			getEventPropertiesResp.getTopicExpressionDialect().forEach(x -> System.out.println(x));
			for (Object object : getEventPropertiesResp.getTopicSet().getAny()) {
				Element e = (Element) object;
				WsNotificationTest.printTree(e, e.getNodeName());
			}


		}


		PTZ ptz = device.getPtz();
		if (ptz != null) {

			out += "PTZ:"  + sep;
			String profileToken = profiles.get(0).getToken();

			Capabilities ptz_caps = ptz.getServiceCapabilities();
			out += "\tgetServiceCapabilities=" + OnvifUtils.format(ptz_caps) + sep;
			PTZStatus s = ptz.getStatus(profileToken);
			out += "\tgetStatus=" + OnvifUtils.format(s) + sep;
			// out += "ptz.getConfiguration=" + ptz.getConfiguration(profileToken) + sep;
			List<PTZPreset> presets = ptz.getPresets(profileToken);
			if (presets != null && !presets.isEmpty()) {
				out += "\tPresets:" + presets.size() +sep;
				for (PTZPreset p : presets)
					out += "\t\t" + OnvifUtils.format(p) + sep;
			}
		}


		return out;
	}

	public static String testCamera(URL url, String user, String password) throws SOAPException, IOException {
		OnvifDevice device = new OnvifDevice(url, user, password);
		return inspect(device);
	}


	public static void main(String[] args) {
		try {
			OnvifCredentials creds = GetTestDevice.getOnvifCredentials(args);
			String out = testCamera(creds);
			LOG.info("\n"+out+"\n");
		} catch (Throwable th) {
			th.printStackTrace();
		}
	}

}
