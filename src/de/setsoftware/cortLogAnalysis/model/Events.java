package de.setsoftware.cortLogAnalysis.model;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

public class Events implements Iterable<Event> {

    private final List<Event> events;

    public Events(final List<Event> events) {
        this.events = events;
        Collections.sort(events, Comparator.comparing(Event::getTimestamp));
    }

    public static Events load(final File directory)
            throws SAXException, IOException, ParserConfigurationException, DatatypeConfigurationException {
        return new Events(getEventsIn(directory));
    }

    public static Events loadDefault()
            throws SAXException, IOException, ParserConfigurationException, DatatypeConfigurationException {
        return Events.load(new File("data\\Review"));
    }

    private static List<Event> getEventsIn(final File directory)
            throws SAXException, IOException, ParserConfigurationException, DatatypeConfigurationException {
        final List<Event> ret = new ArrayList<>();
        for (final File child : directory.listFiles()) {
            if (child.isDirectory()) {
                ret.addAll(getEventsIn(child));
            } else {
                ret.add(Event.load(child));
            }
        }
        return ret;
    }

    public<T> Set<T> mapToSet(final Function<Event, T> selector) {
        return this.events.stream().map(selector).collect(Collectors.toSet());
    }

    public<T> List<Events> groupBy(final Function<Event, T> selector) {
        final Map<T, List<Event>> map = this.events.stream().collect(Collectors.groupingBy(selector));
        return map.values().stream().map(l -> new Events(l)).collect(Collectors.toList());
    }

    public Events filter(final Predicate<? super Event> predicate) {
        return new Events(this.events.stream().filter(predicate).collect(Collectors.toList()));
    }

    public<T extends Comparable<T>> T min(final Function<Event, T> selector) {
        return this.events.stream().map(selector).min(Comparator.naturalOrder()).get();
    }

    public<T extends Comparable<T>> T max(final Function<Event, T> selector) {
        return this.events.stream().map(selector).max(Comparator.naturalOrder()).get();
    }

    public int size() {
        return this.events.size();
    }

    public Event getExample() {
        return this.events.get(0);
    }

    public void normalizeUsers() {
        //Problem in frühen Versionen: manchmal User mit Großbuchstaben, manchmal mit Kleinbuchstaben
        final Map<String, String> usersMap = new HashMap<>();
        usersMap.put("67f994533a5d976eed69aeae05e381bf6fa851e8", "0e0bd9224cae3992bdb822021f1daed06a2e0a72");
        usersMap.put("a91dcbb4df49d8bfe0a9930d7ff911a4292ca1f5", "2c859ac5409f6b5f6e256e413d66eec4394853b2");
        usersMap.put("7b21fdd90a6056168b4b04da4c8c387c4fbce2f5", "fdf943dd853f60e8f066b9ff1db365525dfa7714");
        usersMap.put("bc811258ae9f9fcb0494bb2785a876a59993024e", "7c4c910b08dc857dcb4cf5da2372858fb614226b");
        usersMap.put("df211ccdd94a63e0bcb9e6ae427a249484a49d60", "d00bb3f3b7c7b8815b6dcf237dd16aab9744eca8");
        usersMap.put("a454492e42fd9810e577ebee548c7e59bd883bca", "56594b38808d9c32652dc3b65e7cd14ed265277e");
        usersMap.put("3e76c24356d30b633982c828d9ab9b2144060f6b", "76e8daf43b51c7469b266d37aab6e3ed52f679bc");
        usersMap.put("8c1efea6bf9b8e3cc16fcc40576fdc7118cff0dd", "4c205cfc2e35806118b389e25fd798b1bf1a60ac");
        usersMap.put("363eb224f6ff8d3c5163a8805222acbf939a65b3", "9fcb8e351fa873324f5b111196609d0dfbd09a10");
        usersMap.put("fbce2c364a46f4c8162dfc1f0473ca824a2a2195", "f8a902b067d4344b687920f92c669bffad7b0e0e");
        usersMap.put("a7ae639ea556171c56cd06c61c2cfd09ccbee0a9", "033a22901425797f624e162970fc5183dfda59c4");
        usersMap.put("dcab2065d59495532bbce07c9af6c778f6afe178", "dc8d73b2300ecd72072137237d2c68dc0d3a7981");
        usersMap.put("23d8e0156062165ca3736e9f1e364d414e1d82d5", "62a3ad0fef668c4e2a220f6982de94942fbf1d1e");
        usersMap.put("da23614e02469a0d7c7bd1bdab5c9c474b1904dc", "06d945942aa26a61be18c3e22bf19bbca8dd2b5d");

        final ListIterator<Event> iter = this.events.listIterator();
        while (iter.hasNext()) {
            final Event event = iter.next();
            final String translated = usersMap.get(event.getUser());
            if (translated != null) {
                iter.set(new Event(
                        translated,
                        event.getTimestamp(),
                        event.getTool(),
                        event.getDataType(),
                        event.getResource(),
                        event.getProperties()));
            }
        }
    }

    public List<Events> splitIntoSessions() {
        final List<Events> ret = new ArrayList<>();
        for (final Events keyGroup : this.groupBy(Event::getResource)) {
            for (final Events userGroup : keyGroup.groupBy(Event::getUser)) {
                ret.add(userGroup);
            }
        }
        return ret;
    }

    public Instant minTime() {
        return this.min(Event::getTimestamp);
    }

    public Instant maxTime() {
        return this.max(Event::getTimestamp);
    }

    public boolean containsEventType(final String eventType) {
        return this.events.stream().anyMatch(e -> e.getDataType().equals(eventType));
    }

    public long countEventType(final String eventType) {
        return this.events.stream().filter(e -> e.getDataType().equals(eventType)).count();
    }

    public String getTypeOfFirstSession() {
        return this.getFirstSessionStartEvent().map(Event::getDataType).orElse("?");
    }

    private Optional<Event> getFirstSessionStartEvent() {
        return this.events.stream().filter(Event::isSessionStart).findFirst();
    }

    public int getRoundFromFirstSessionStart() {
        return Integer.parseInt(this.getFirstSessionStartEvent().flatMap(e -> e.getProperty("round")).orElse("-1"));
    }

    public String getToolFromFirstEvent() {
        return this.events.get(0).getTool();
    }

    public void discardVersionsWithOldKeys() {
        final Iterator<Event> iter = this.events.iterator();
        while (iter.hasNext()) {
            final Event e = iter.next();
            if (!e.hasNewKey()) {
                iter.remove();
            }
        }
    }

    @Override
    public Iterator<Event> iterator() {
        return this.events.iterator();
    }

    /**
     * When the session key is generated, the real round number is not yet known, but when
     * the [review|fixing]Started event is sent, it is. Therefore determine necessary corrections.
     */
    public int getCorrectedRound() {
        if (this.containsEventType("reviewStarted") || this.containsEventType("fixingStarted")) {
            return this.getRoundFromFirstSessionStart();
        } else {
            return this.getExample().getReviewRound();
        }
    }

}
