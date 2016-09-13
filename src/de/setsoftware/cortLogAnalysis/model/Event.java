package de.setsoftware.cortLogAnalysis.model;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Event {

    private final String user;
    private final Instant timestamp;
    private final String tool;
    private final String dataType;
    private final String resource;
    private final Map<String, String> properties;

    public Event(
            final String user,
            final Instant timestamp,
            final String tool,
            final String dataType,
            final String resource,
            final Map<String, String> properties) {
        this.user = user;
        this.timestamp = timestamp;
        this.tool = tool;
        this.dataType = dataType;
        this.resource = resource;
        this.properties = properties;
    }

    public static Event load(final File child)
            throws SAXException, IOException, ParserConfigurationException, DatatypeConfigurationException {
        final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(child);
        final Map<String, String> properties = new LinkedHashMap<>();
        final NodeList propertyNodes = doc.getElementsByTagName("Property");
        for (int i = 0; i < propertyNodes.getLength(); i++) {
            final Element n = (Element) propertyNodes.item(i);
            properties.put(getChildElementContent(n, "Key"), getChildElementContent(n, "Value"));
        }
        return new Event(
                getChildElementContent(doc.getDocumentElement(), "Owner"),
                parseTimestamp(getChildElementContent(doc.getDocumentElement(), "Runtime")),
                getChildElementContent(doc.getDocumentElement(), "Tool"),
                getChildElementContent(doc.getDocumentElement(), "SensorDataType"),
                getChildElementContent(doc.getDocumentElement(), "Resource"),
                properties);
    }

    private static Instant parseTimestamp(final String s) throws DatatypeConfigurationException {
        return DatatypeFactory.newInstance().newXMLGregorianCalendar(s).toGregorianCalendar().toInstant();
    }

    private static String getChildElementContent(final Element e, final String elementName) {
        return e.getElementsByTagName(elementName).item(0).getTextContent();
    }

    public String getUser() {
        return this.user;
    }

    public Instant getTimestamp() {
        return this.timestamp;
    }

    public String getTool() {
        return this.tool;
    }

    public String getDataType() {
        return this.dataType;
    }

    public String getResource() {
        return this.resource;
    }

    public Map<String, String> getProperties() {
        return this.properties;
    }

    public boolean isSessionStart() {
        return this.dataType.equals("reviewStarted")
            || this.dataType.equals("fixingStarted");
    }

    public Optional<String> getProperty(final String key) {
        return Optional.ofNullable(this.properties.get(key));
    }

    public boolean hasNewKey() {
        return this.resource.indexOf(',') >= 0;
    }

    public String getTicketKey() {
        return this.splitResource()[0];
    }

    public String getSessionType() {
        return this.splitResource()[1];
    }

    public String getReviewRound() {
        return this.splitResource()[2];
    }

    public String getSessionId() {
        return this.splitResource()[3];
    }

    private String[] splitResource() {
        return this.resource.split(",");
    }

}
