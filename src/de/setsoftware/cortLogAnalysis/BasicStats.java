package de.setsoftware.cortLogAnalysis;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import de.setsoftware.cortLogAnalysis.model.Event;
import de.setsoftware.cortLogAnalysis.model.Events;

public class BasicStats {

    private static Map<String, String> lookupMap = new HashMap<>();

    static {
        registerLookup("");
        registerLookup("TB");
        registerLookup("tb");
        registerLookup("CS");
        registerLookup("cs");
        registerLookup("MC");
        registerLookup("mc");
        registerLookup("JH");
        registerLookup("jh");
        registerLookup("SU");
        registerLookup("su");
        registerLookup("JU");
        registerLookup("ju");
        registerLookup("MK");
        registerLookup("mk");
        registerLookup("AS");
        registerLookup("as");
        registerLookup("AR");
        registerLookup("ar");
        registerLookup("PT");
        registerLookup("pt");
        registerLookup("SM");
        registerLookup("sm");
        registerLookup("AB");
        registerLookup("ab");
        registerLookup("NW");
        registerLookup("nw");
        registerLookup("UR");
        registerLookup("ur");
        registerLookup("AN");
        registerLookup("an");
        registerLookup("MI");
        registerLookup("mi");
        registerLookup("WR");
        registerLookup("wr");
    }

    private static void registerLookup(final String string) {
        lookupMap.put(obfuscate(string), string);
    }

    /**
    * Hashes the given string so that the real value is not directly visible.
    */
    protected static String obfuscate(final String data) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(data.getBytes("UTF-8"));
            final byte[] mdbytes = md.digest();
            final StringBuilder sb = new StringBuilder();
            for (final byte mdbyte : mdbytes) {
                final int b = 0xFF & mdbyte;
                if (b < 16) {
                    sb.append('0').append(Integer.toHexString(b));
                } else {
                    sb.append(Integer.toHexString(b));
                }
            }
            return sb.toString();
        } catch (final NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new Error(e);
        }
    }

    private static String reverseLookup(final String string) {
        final String s = lookupMap.get(string);
        return s == null ? "?" : s;
    }

    public static void main(final String[] args) throws Exception {
        for (final Entry<String, String> e1 : lookupMap.entrySet()) {
            for (final Entry<String, String> e2 : lookupMap.entrySet()) {
                final String clr1 = e1.getValue();
                final String clr2 = e2.getValue();
                if (!clr1.equals(clr1.toUpperCase()) && clr2.equals(clr2.toUpperCase()) && clr1.toUpperCase().equals(clr2)) {
                    System.out.println(e1 + "-" + e2);
                }
            }
        }
//        final Events events = Events.loadDefault();
//        events.normalizeUsers();
//
//        System.out.println("== Users");
//        final List<Events> users = events.groupBy(Event::getUser);
//        Collections.sort(users, (e1, e2) -> e1.max(Event::getTimestamp).compareTo(e2.max(Event::getTimestamp)));
//        for (final Events eventsForUser : users) {
//            final String user = eventsForUser.getExample().getUser();
//            final Instant minTimestamp = eventsForUser.min(Event::getTimestamp);
//            final Instant maxTimestamp = eventsForUser.max(Event::getTimestamp);
//            System.out.println("User = " + user + " (" + reverseLookup(user) + ")");
//            System.out.println("Events for user = " + eventsForUser.size());
//            System.out.println("Earliest time = " + minTimestamp);
//            System.out.println("Latest time = " + maxTimestamp);
//            System.out.println();
//        }
//
//        System.out.println("== Versions");
//        final List<Events> versions = groupedStats(events, Event::getTool);
//
//        System.out.println("== Data types");
//        final List<Events> dataTypes = groupedStats(events, Event::getDataType);
//
//        System.out.println("== Total");
//        System.out.println("Events: " + events.size());
//        System.out.println("Users: " + users.size());
//        System.out.println("Versions: " + versions.size());
//        System.out.println("Data types: " + dataTypes.size());
    }

    private static List<Events> groupedStats(final Events events, final Function<Event, String> selector) {
        final List<Events> versions = events.groupBy(selector);
        Collections.sort(versions, (e1, e2) -> e1.max(Event::getTimestamp).compareTo(e2.max(Event::getTimestamp)));
        for (final Events eventsForGroup : versions) {
            final String tool = selector.apply(eventsForGroup.getExample());
            final Instant minTimestamp = eventsForGroup.minTime();
            final Instant maxTimestamp = eventsForGroup.maxTime();
            System.out.println("Group = " + tool);
            System.out.println("Event count = " + eventsForGroup.size());
            System.out.println("Earliest time = " + minTimestamp);
            System.out.println("Latest time = " + maxTimestamp);
            System.out.println();
        }
        return versions;
    }

}
