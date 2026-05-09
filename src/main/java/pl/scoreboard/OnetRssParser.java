package pl.scoreboard;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URL;

public class OnetRssParser {
    private static final String RSS_URL = "https://wiadomosci.onet.pl/.feed";

    public static String getLatestNews() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new URL(RSS_URL).openStream());
            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName("item");
            if (nList.getLength() > 0) {
                Element firstItem = (Element) nList.item(0);
                return firstItem.getElementsByTagName("title").item(0).getTextContent();
            }
        } catch (Exception e) {
            return "Błąd pobierania wiadomości";
        }
        return "Brak nowych wiadomości";
    }
}