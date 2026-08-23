package com.akscrobble.core;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public final class LastfmResponse {
    public final String status;
    public final int errorCode;
    public final String errorMessage;
    public final int accepted;
    public final int ignored;
    public final List<Integer> ignoredCodes;
    public final String sessionKey;
    public final String sessionName;
    public final String token;
    public final boolean transportError;

    private LastfmResponse(String status, int errorCode, String errorMessage,
            int accepted, int ignored, List<Integer> ignoredCodes, String sessionKey,
            String sessionName, String token, boolean transportError) {
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.accepted = accepted;
        this.ignored = ignored;
        this.ignoredCodes = Collections.unmodifiableList(new ArrayList<Integer>(ignoredCodes));
        this.sessionKey = sessionKey;
        this.sessionName = sessionName;
        this.token = token;
        this.transportError = transportError;
    }

    public static LastfmResponse parse(String xml) {
        if (xml == null || xml.trim().length() == 0) return transportError("Empty response");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            try {
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {}
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = doc.getDocumentElement();
            // プロキシや CDN が 5xx で返す HTML エラーページは XML としては整形式なので
            // ここまで到達してしまう。root が <lfm> でない、あるいは status が
            // ok/failed のどちらでもないものは Last.fm の応答ではないと判断し、
            // transport error（＝リトライ可）に倒す。恒久エラー扱いにすると
            // 一時的な 502 で scrobble を永久に捨てることになる。
            if (root == null || !"lfm".equals(root.getNodeName())) {
                return transportError("Unexpected root element: "
                        + (root == null ? "(none)" : root.getNodeName()));
            }
            String rootStatus = root.getAttribute("status");
            if (!"ok".equalsIgnoreCase(rootStatus) && !"failed".equalsIgnoreCase(rootStatus)) {
                return transportError("Unexpected lfm status: '" + rootStatus + "'");
            }
            Element error = first(root, "error");
            Element scrobbles = first(root, "scrobbles");
            Element session = first(root, "session");
            List<Integer> ignoredCodes = new ArrayList<Integer>();
            NodeList ignoredMessages = root.getElementsByTagName("ignoredMessage");
            for (int i = 0; i < ignoredMessages.getLength(); i++) {
                ignoredCodes.add(Integer.valueOf(intAttr((Element) ignoredMessages.item(i), "code")));
            }
            return new LastfmResponse(root.getAttribute("status"),
                    error == null ? 0 : intAttr(error, "code"), text(error),
                    scrobbles == null ? 0 : intAttr(scrobbles, "accepted"),
                    scrobbles == null ? 0 : intAttr(scrobbles, "ignored"), ignoredCodes,
                    childText(session, "key"), childText(session, "name"),
                    childText(root, "token"), false);
        } catch (Exception e) {
            return transportError("Invalid XML: " + e.getMessage());
        }
    }

    public static LastfmResponse transportError(String message) {
        return new LastfmResponse("failed", 0, message, 0, 0,
                Collections.<Integer>emptyList(), null, null, null, true);
    }

    public boolean isOk() { return "ok".equalsIgnoreCase(status) && !transportError; }

    public boolean isAuthFailure() {
        return !isOk() && (errorCode == 4 || errorCode == 9 || errorCode == 10
                || errorCode == 13 || errorCode == 26);
    }

    public boolean isRetryable() {
        return !isOk() && (transportError || errorCode == 8 || errorCode == 11
                || errorCode == 16 || errorCode == 29);
    }

    public boolean isPermanent() { return !isOk() && !isRetryable(); }

    private static Element first(Element parent, String tag) {
        if (parent == null) return null;
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static String childText(Element parent, String tag) { return text(first(parent, tag)); }

    private static String text(Element element) {
        return element == null ? null : element.getTextContent();
    }

    private static int intAttr(Element element, String name) {
        try { return Integer.parseInt(element.getAttribute(name)); }
        catch (NumberFormatException e) { return 0; }
    }
}
