package catsurvivors;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port forwarding automatico via UPnP-IGD, in puro Java standard.
 * Quando si ospita una partita, il gioco chiede al router di inoltrare la porta TCP
 * verso questo PC, così gli amici fuori dalla LAN possono connettersi all'IP pubblico.
 * Tutto best-effort: se il router non supporta UPnP (o è disattivato) si fallisce in modo pulito.
 */
final class Upnp {
    private static final String MCAST = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int TIMEOUT = 4000;

    /** Esito di un tentativo di apertura porta, riusato anche per la chiusura. */
    static final class Result {
        boolean mapped;
        String externalIp;     // IP pubblico visto dal router (può essere null)
        String message = "";
        String controlUrl;     // endpoint SOAP del servizio WAN
        String serviceType;
        String internalIp;
        int externalPort;
    }

    private record Gateway(String controlUrl, String serviceType) {}

    private Upnp() {}

    /** Scopre il router, gli chiede di aprire la porta e ne ricava l'IP pubblico. */
    static Result openPort(int port, String internalIp) {
        Result r = new Result();
        r.externalPort = port;
        r.internalIp = internalIp;
        try {
            Gateway gw = discover();
            if (gw == null) {
                r.message = "Nessun router UPnP trovato (UPnP forse disattivato sul router).";
                return r;
            }
            r.controlUrl = gw.controlUrl();
            r.serviceType = gw.serviceType();
            try {
                r.externalIp = getExternalIp(gw);
            } catch (Exception ignored) { /* l'IP pubblico è un di più */ }
            r.mapped = addMapping(gw, port, internalIp);
            r.message = r.mapped
                    ? "Porta " + port + " aperta sul router via UPnP."
                    : "Il router è stato trovato ma ha rifiutato la mappatura UPnP.";
        } catch (Exception e) {
            r.message = "UPnP non riuscito: " + e.getMessage();
        }
        return r;
    }

    /** Rimuove la mappatura creata da openPort (da chiamare alla chiusura della lobby). */
    static void closePort(Result r) {
        if (r == null || !r.mapped || r.controlUrl == null) return;
        try {
            String body = soapBody(r.serviceType, "DeletePortMapping",
                    "<NewRemoteHost></NewRemoteHost>"
                    + "<NewExternalPort>" + r.externalPort + "</NewExternalPort>"
                    + "<NewProtocol>TCP</NewProtocol>");
            soap(r.controlUrl, r.serviceType, "DeletePortMapping", body);
        } catch (Exception ignored) { /* best effort */ }
    }

    // ===== Discovery SSDP =====

    private static final String[] TARGETS = {
        "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
        "urn:schemas-upnp-org:service:WANIPConnection:1",
        "urn:schemas-upnp-org:service:WANPPPConnection:1",
        "upnp:rootdevice",
    };

    private static Gateway discover() throws IOException {
        Set<String> locations = new LinkedHashSet<>();
        // sonda da ogni interfaccia LAN (un PC ha spesso Wi-Fi, Ethernet, Hyper-V, VPN:
        // il multicast deve uscire dalla scheda giusta per raggiungere il router)
        for (InetAddress local : localIps()) {
            searchOn(local, locations);
            for (String loc : locations) {
                Gateway gw = serviceFrom(loc);
                if (gw != null) return gw;
            }
        }
        // ultimo tentativo: socket non vincolato (interfaccia scelta dall'OS)
        searchOn(null, locations);
        for (String loc : locations) {
            Gateway gw = serviceFrom(loc);
            if (gw != null) return gw;
        }
        return null;
    }

    private static void searchOn(InetAddress local, Set<String> locations) {
        try (DatagramSocket sock = local != null
                ? new DatagramSocket(0, local) : new DatagramSocket()) {
            sock.setSoTimeout(TIMEOUT);
            sock.setReuseAddress(true);
            InetAddress group = InetAddress.getByName(MCAST);
            for (String st : TARGETS) {
                String msg = "M-SEARCH * HTTP/1.1\r\n"
                        + "HOST: " + MCAST + ":" + SSDP_PORT + "\r\n"
                        + "MAN: \"ssdp:discover\"\r\n"
                        + "MX: 2\r\n"
                        + "ST: " + st + "\r\n\r\n";
                byte[] b = msg.getBytes(StandardCharsets.US_ASCII);
                sock.send(new DatagramPacket(b, b.length, group, SSDP_PORT));
            }
            long deadline = System.nanoTime() + TIMEOUT * 1_000_000L;
            byte[] buf = new byte[2048];
            while (System.nanoTime() < deadline) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    sock.receive(pkt);
                    String resp = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.US_ASCII);
                    String loc = header(resp, "location");
                    if (loc != null) locations.add(loc.trim());
                } catch (IOException timeout) {
                    break;
                }
            }
        } catch (IOException ignored) { /* questa interfaccia non va: prova la prossima */ }
    }

    private static java.util.List<InetAddress> localIps() {
        java.util.List<InetAddress> out = new java.util.ArrayList<>();
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifs = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                java.net.NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress()) out.add(a);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Scarica la descrizione del dispositivo e individua il servizio WAN. */
    private static Gateway serviceFrom(String location) {
        try {
            String xml = httpGet(location);
            Document doc = parse(xml);
            String urlBase = text(doc.getElementsByTagName("URLBase"));
            String base = (urlBase != null && !urlBase.isBlank()) ? urlBase.trim() : location;

            NodeList services = doc.getElementsByTagName("service");
            for (int i = 0; i < services.getLength(); i++) {
                Element svc = (Element) services.item(i);
                String type = child(svc, "serviceType");
                String ctrl = child(svc, "controlURL");
                if (type == null || ctrl == null) continue;
                if (type.contains("WANIPConnection") || type.contains("WANPPPConnection")) {
                    String controlUrl = new URL(new URL(base), ctrl).toString();
                    return new Gateway(controlUrl, type);
                }
            }
        } catch (Exception ignored) { /* prova la prossima location */ }
        return null;
    }

    // ===== Chiamate SOAP =====

    private static boolean addMapping(Gateway gw, int port, String internalIp) throws IOException {
        String body = soapBody(gw.serviceType(), "AddPortMapping",
                "<NewRemoteHost></NewRemoteHost>"
                + "<NewExternalPort>" + port + "</NewExternalPort>"
                + "<NewProtocol>TCP</NewProtocol>"
                + "<NewInternalPort>" + port + "</NewInternalPort>"
                + "<NewInternalClient>" + internalIp + "</NewInternalClient>"
                + "<NewEnabled>1</NewEnabled>"
                + "<NewPortMappingDescription>CatSurvivors</NewPortMappingDescription>"
                + "<NewLeaseDuration>0</NewLeaseDuration>");
        int code = soap(gw.controlUrl(), gw.serviceType(), "AddPortMapping", body);
        return code == 200;
    }

    private static String getExternalIp(Gateway gw) throws IOException {
        String body = soapBody(gw.serviceType(), "GetExternalIPAddress", "");
        StringBuilder resp = new StringBuilder();
        int code = soap(gw.controlUrl(), gw.serviceType(), "GetExternalIPAddress", body, resp);
        if (code != 200) return null;
        Matcher m = Pattern.compile("<NewExternalIPAddress>\\s*([^<\\s]+)\\s*</NewExternalIPAddress>").matcher(resp);
        return m.find() ? m.group(1) : null;
    }

    private static String soapBody(String serviceType, String action, String inner) {
        return "<?xml version=\"1.0\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body>"
                + "<u:" + action + " xmlns:u=\"" + serviceType + "\">" + inner + "</u:" + action + ">"
                + "</s:Body></s:Envelope>";
    }

    private static int soap(String controlUrl, String serviceType, String action, String body) throws IOException {
        return soap(controlUrl, serviceType, action, body, null);
    }

    private static int soap(String controlUrl, String serviceType, String action, String body,
                            StringBuilder out) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(controlUrl).openConnection();
        con.setConnectTimeout(TIMEOUT);
        con.setReadTimeout(TIMEOUT);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        con.setRequestProperty("SOAPAction", "\"" + serviceType + "#" + action + "\"");
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        con.setRequestProperty("Content-Length", String.valueOf(payload.length));
        try (DataOutputStream dos = new DataOutputStream(con.getOutputStream())) {
            dos.write(payload);
        }
        int code = con.getResponseCode();
        if (out != null) {
            InputStream is = code == 200 ? con.getInputStream() : con.getErrorStream();
            if (is != null) out.append(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } else {
            consume(code == 200 ? con.getInputStream() : con.getErrorStream());
        }
        con.disconnect();
        return code;
    }

    // ===== Helper HTTP/XML =====

    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setConnectTimeout(TIMEOUT);
        con.setReadTimeout(TIMEOUT);
        try (InputStream is = con.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            con.disconnect();
        }
    }

    private static void consume(InputStream is) {
        if (is == null) return;
        try (is) {
            is.readAllBytes();
        } catch (IOException ignored) { }
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // descrizione del router: niente DOCTYPE/entità esterne (anti-XXE)
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setExpandEntityReferences(false);
        DocumentBuilder db = f.newDocumentBuilder();
        return db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String header(String resp, String name) {
        for (String line : resp.split("\r\n")) {
            int c = line.indexOf(':');
            if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase(name)) {
                return line.substring(c + 1);
            }
        }
        return null;
    }

    private static String text(NodeList list) {
        return list.getLength() > 0 ? list.item(0).getTextContent() : null;
    }

    private static String child(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n.getParentNode() == parent || isDescendant(parent, n)) return n.getTextContent().trim();
        }
        return list.getLength() > 0 ? list.item(0).getTextContent().trim() : null;
    }

    private static boolean isDescendant(Node ancestor, Node n) {
        for (Node p = n.getParentNode(); p != null; p = p.getParentNode()) {
            if (p == ancestor) return true;
        }
        return false;
    }
}
