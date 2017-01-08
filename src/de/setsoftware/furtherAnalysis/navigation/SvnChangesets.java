package de.setsoftware.furtherAnalysis.navigation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SvnChangesets {

    private static final Pattern TICKET_PATTERN = Pattern.compile(".*(PSY-[0-9]+)[^0-9].*");
    private final Map<String, TreeSet<String>> content = new HashMap<>();

    public static SvnChangesets load() throws IOException {
        try (BufferedReader r = new BufferedReader(new FileReader("svndump.txt"))) {
            return load(r);
        }
    }

    private static SvnChangesets load(BufferedReader r) throws IOException {
        boolean inMessage = false;
        String ticketNumber = null;
        String line;
        final SvnChangesets ret = new SvnChangesets();
        while ((line = r.readLine()) != null) {
            if (line.equals("Message:")) {
                inMessage = true;
                ticketNumber = null;
            } else if (line.equals("----")) {
                inMessage = false;
            } else if (inMessage && ticketNumber == null) {
                final Matcher m = TICKET_PATTERN.matcher(line);
                if (m.matches()) {
                    ticketNumber = m.group(1);
                    ret.content.put(ticketNumber, new TreeSet<>());
                }
            } else {
                if (ticketNumber != null && !ticketNumber.equals("PSY-0")) {
                    String file = null;
                    if (line.startsWith("Modified : ") && isFile(line)) {
                        file = extractFile(line);
                    } else if (line.startsWith("Added : ") && isFile(line)) {
                        file = extractFile(line);
//                    } else if (line.startsWith("Deleted : ") && isFile(line)) {
//                        file = extractFile(line);
                    }
                    if (file != null) {
                        ret.content.get(ticketNumber).add(file);
                    }
                }
            }
        }
        return ret;
    }

    private static boolean isFile(String line) {
        //the log does not contain information whether an entry is a directory or a file
        //   therefore regard everything with a dot in it as file
        return line.contains(".");
    }

    private static String extractFile(String line) {
        final int colonIndex = line.indexOf(':');
        String afterColon = line.substring(colonIndex + 1);
        final int copyIndex = afterColon.indexOf(" (Copy final from path:");
        if (copyIndex > 0) {
            afterColon = afterColon.substring(0, copyIndex);
        }
        return afterColon.trim().replace("/trunk/Workspace/", "");
    }

    public TreeSet<String> get(String ticketKey) {
        return this.content.get(ticketKey);
    }

}
