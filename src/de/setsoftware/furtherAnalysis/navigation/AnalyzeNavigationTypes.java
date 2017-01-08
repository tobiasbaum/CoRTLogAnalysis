package de.setsoftware.furtherAnalysis.navigation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class AnalyzeNavigationTypes {

    public static void main(String[] args) throws IOException {
        final SvnChangesets svn = SvnChangesets.load();

        final BufferedReader r = new BufferedReader(new FileReader("C:\\Users\\ich\\Documents\\alberto_change_ordering\\cort_navigation_data\\rawNavigation.csv"));
        final String header = r.readLine();
        String line;
        String currentSessionKey = "";
        NavigationSession.printHeader();
        NavigationSession curSession = null;
        while ((line = r.readLine()) != null) {
            final String[] parts = line.split(";");
            final String ticket = stripAndCheckQuotes(parts[0]);
            final String user = stripAndCheckQuotes(parts[1]);
            final String session = stripAndCheckQuotes(parts[3]);
            final String eventType = stripAndCheckQuotes(parts[5]);
            final String files = stripAndCheckQuotes(parts[6]);
            final String jumpType = stripAndCheckQuotes(parts[7]);

            final String key = session + ";" + user;
            if (!currentSessionKey.equals(key)) {
                if (curSession != null) {
                    curSession.print();
                }
                currentSessionKey = key;
                curSession = new NavigationSession(svn, ticket, user, session);
            }
            curSession.addEvent(eventType, files, jumpType);
        }
        if (curSession != null) {
            curSession.print();
        }
        r.close();
    }

    private static String stripAndCheckQuotes(String string) {
        if (!string.startsWith("\"")) {
            throw new RuntimeException("parse error: " + string);
        }
        if (!string.endsWith("\"")) {
            throw new RuntimeException("parse error: " + string);
        }
        return string.substring(1, string.length() - 1);
    }

}
