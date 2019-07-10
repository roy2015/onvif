package de.onvif.soap;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.xml.soap.SOAPException;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;

import org.apache.cxf.binding.soap.Soap12;
import org.apache.cxf.binding.soap.SoapBindingConfiguration;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.cxf.wsn.client.CreatePullPoint;
import org.onvif.ver10.device.wsdl.Device;
import org.onvif.ver10.device.wsdl.DeviceService;
import org.onvif.ver10.events.wsdl.EventPortType;
import org.onvif.ver10.events.wsdl.EventService;
import org.onvif.ver10.media.wsdl.Media;
import org.onvif.ver10.media.wsdl.MediaService;
import org.onvif.ver10.schema.Capabilities;
import org.onvif.ver10.schema.CapabilityCategory;
import org.onvif.ver10.schema.DateTime;
import org.onvif.ver10.schema.SetDateTimeType;
import org.onvif.ver20.imaging.wsdl.ImagingPort;
import org.onvif.ver20.imaging.wsdl.ImagingService;
import org.onvif.ver20.ptz.wsdl.PTZ;
import org.onvif.ver20.ptz.wsdl.PtzService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.onvif.beans.DeviceInfo;

/**
 * 
 * @author Robin Dick
 * 
 */
public class OnvifDevice {
	private static final Logger logger = LoggerFactory.getLogger(OnvifDevice.class);

	private String deviceIp;
	private String username, password;
	private String deviceUri;

	private Device device;
	private Media media;
	private PTZ ptz;
	private ImagingPort imaging;
	private EventPortType events;

	/**
	 * Initializes an Onvif device, e.g. a Network Video Transmitter (NVT) with
	 * logindata.
	 * 
	 * @param deviceIp
	 *            The IP address of your device, you can also add a port but not
	 *            protocol (e.g. http://)
	 * @param user
	 *            Username you need to login
	 * @param password
	 *            User's password to login
	 * @throws ConnectException
	 *             Exception gets thrown, if device isn't accessible or invalid
	 *             and doesn't answer to SOAP messages
	 * @throws SOAPException
	 */
	public OnvifDevice(String deviceIp, String user, String password) throws ConnectException, SOAPException {
		this.deviceIp = deviceIp;
		
		if (!isOnline()) {
			throw new ConnectException("Host not available.");
		}

		this.deviceUri = "http://" + deviceIp + "/onvif/device_service";
		this.username = user;
		this.password = password;

		init();
	}

	/**
	 * Initializes an Onvif device, e.g. a Network Video Transmitter (NVT) with
	 * logindata.
	 * 
	 * @param hostIp
	 *            The IP address of your device, you can also add a port but
	 *            noch protocol (e.g. http://)
	 * @throws ConnectException
	 *             Exception gets thrown, if device isn't accessible or invalid
	 *             and doesn't answer to SOAP messages
	 * @throws SOAPException
	 */
	public OnvifDevice(String hostIp) throws ConnectException, SOAPException {
		this(hostIp, null, null);
	}

	/**
	 * Internal function to check, if device is available and answers to ping
	 * requests.
	 */
	private boolean isOnline() {
		String port = deviceIp.contains(":") ? deviceIp.substring(deviceIp.indexOf(':') + 1) : "80";
		String ip = deviceIp.contains(":") ? deviceIp.substring(0, deviceIp.indexOf(':')) : deviceIp;

		Socket socket = null;
		try {
			SocketAddress sockaddr = new InetSocketAddress(ip, Integer.parseInt(port));
			socket = new Socket();

			socket.connect(sockaddr, 5000);
		} catch (NumberFormatException | IOException e) {
			return false;
		} finally {
			try {
				if (socket != null) {
					socket.close();
				}
			} catch (IOException ex) {
			}
		}
		return true;
	}

	/**
	 * Initalizes the addresses used for SOAP messages and to get the internal
	 * IP, if given IP is a proxy.
	 * 
	 * @throws ConnectException
	 *             Get thrown if device doesn't give answers to
	 *             GetCapabilities()
	 * @throws SOAPException
	 */
	protected void init() throws ConnectException, SOAPException {
		BindingProvider deviceServicePort = (BindingProvider) new DeviceService().getDevicePort();
		this.device = getServiceProxy(deviceServicePort, deviceUri).create(Device.class);
		
		resetSystemDateAndTime();
		
		Capabilities capabilities = this.device.getCapabilities(Arrays.asList(CapabilityCategory.ALL));
		if (capabilities == null) {
			throw new ConnectException("Capabilities not reachable.");
		}

		//String localDeviceUri = capabilities.getDevice().getXAddr();

		if (capabilities.getMedia() != null && capabilities.getMedia().getXAddr() != null) {
			this.media = new MediaService().getMediaPort();
			this.media = getServiceProxy((BindingProvider) media, capabilities.getMedia().getXAddr()).create(Media.class);
		}

		if (capabilities.getPTZ() != null && capabilities.getPTZ().getXAddr() != null) {
			this.ptz = new PtzService().getPtzPort();
			this.ptz = getServiceProxy((BindingProvider) ptz, capabilities.getPTZ().getXAddr()).create(PTZ.class);
		}

		if (capabilities.getImaging() != null && capabilities.getImaging().getXAddr() != null) {
			this.imaging = new ImagingService().getImagingPort();
			this.imaging = getServiceProxy((BindingProvider) imaging, capabilities.getImaging().getXAddr()).create(ImagingPort.class);
		}

		if (capabilities.getEvents() != null && capabilities.getEvents().getXAddr() != null) {
			this.events = new EventService().getEventPort();
			this.events = getServiceProxy((BindingProvider) events, capabilities.getEvents().getXAddr()).create(EventPortType.class);
		}
	}

	public static JaxWsProxyFactoryBean getServiceProxy(BindingProvider servicePort, String serviceAddr) {
		JaxWsProxyFactoryBean proxyFactory = new JaxWsProxyFactoryBean();
		if(serviceAddr != null)
			proxyFactory.setAddress(serviceAddr);
		proxyFactory.setServiceClass(servicePort.getClass());
		proxyFactory.getOutInterceptors().add(new LoggingOutInterceptor());  
		SoapBindingConfiguration config = new SoapBindingConfiguration();  
		config.setVersion(Soap12.getInstance());
		proxyFactory.setBindingConfig(config);
		Client deviceClient = ClientProxy.getClient(servicePort);

		HTTPConduit http = (HTTPConduit) deviceClient.getConduit();

//		AuthorizationPolicy authPolicy = new AuthorizationPolicy();
//		authPolicy.setUserName(username);
//		authPolicy.setPassword(password);
//		authPolicy.setAuthorizationType("Basic");
//		http.setAuthorization(authPolicy);
		
		HTTPClientPolicy httpClientPolicy = http.getClient();
		httpClientPolicy.setConnectionTimeout(36000);  
		httpClientPolicy.setReceiveTimeout(32000);
		httpClientPolicy.setAllowChunking(false);
		return proxyFactory;
	}
	
	private void resetSystemDateAndTime() {
		Calendar calendar = Calendar.getInstance();
		Date currentDate = new Date();
		boolean daylightSavings = calendar.getTimeZone().inDaylightTime(currentDate);
		org.onvif.ver10.schema.TimeZone timeZone = new org.onvif.ver10.schema.TimeZone();
		timeZone.setTZ(displayTimeZone(calendar.getTimeZone()));
		org.onvif.ver10.schema.Time time = new org.onvif.ver10.schema.Time();
		time.setHour(calendar.get(Calendar.HOUR_OF_DAY));
		time.setMinute(calendar.get(Calendar.MINUTE));
		time.setSecond(calendar.get(Calendar.SECOND));
		org.onvif.ver10.schema.Date date = new org.onvif.ver10.schema.Date();
		date.setYear(calendar.get(Calendar.YEAR));
		date.setMonth(calendar.get(Calendar.MONTH) + 1);
		date.setDay(calendar.get(Calendar.DAY_OF_MONTH));
		org.onvif.ver10.schema.DateTime utcDateTime = new org.onvif.ver10.schema.DateTime();
		utcDateTime.setDate(date);
		utcDateTime.setTime(time);
		device.setSystemDateAndTime(SetDateTimeType.MANUAL, daylightSavings, timeZone, utcDateTime);
	}

	private static String displayTimeZone(TimeZone tz) {

		long hours = TimeUnit.MILLISECONDS.toHours(tz.getRawOffset());
		long minutes = TimeUnit.MILLISECONDS.toMinutes(tz.getRawOffset()) - TimeUnit.HOURS.toMinutes(hours);
		// avoid -4:-30 issue
		minutes = Math.abs(minutes);

		String result = "";
		if (hours > 0) {
			result = String.format("GMT+%02d:%02d", hours, minutes);
		} else {
			result = String.format("GMT%02d:%02d", hours, minutes);
		}

		return result;

	}

	/**
	 * Is used for basic devices and requests of given Onvif Device
	 */
	public Device getDevice() {
		return device;
	}

	public PTZ getPtz() {
		return ptz;
	}

	public Media getMedia() {
		return media;
	}

	public ImagingPort getImaging() {
		return imaging;
	}

	public EventPortType getEvents() {
		return events;
	}

	public DateTime getDate() {
		return device.getSystemDateAndTime().getLocalDateTime();
	}

	public DeviceInfo getDeviceInfo() {
		Holder<String> manufacturer = new Holder<>();
		Holder<String> model = new Holder<>();
		Holder<String> firmwareVersion = new Holder<>();
		Holder<String> serialNumber = new Holder<>();
		Holder<String> hardwareId = new Holder<>();
		device.getDeviceInformation(manufacturer, model, firmwareVersion, serialNumber, hardwareId);
		return new DeviceInfo(manufacturer.value, model.value, firmwareVersion.value, serialNumber.value,
				hardwareId.value);
	}

	public String getHostname() {
		return device.getHostname().getName();
	}

	public String reboot() throws ConnectException, SOAPException {
		return device.systemReboot();
	}
}
