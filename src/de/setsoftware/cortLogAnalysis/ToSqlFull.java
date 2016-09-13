package de.setsoftware.cortLogAnalysis;

import java.io.File;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.setsoftware.cortLogAnalysis.model.Event;
import de.setsoftware.cortLogAnalysis.model.Events;

public class ToSqlFull {

    private static final class EventProperty {
        private final Event event;
        private final String name;

        public EventProperty(Event event, String name) {
            this.event = event;
            this.name = name;
        }
    }

    public static void main(String[] args) throws Exception {
        final Events events = Events.loadDefault();
        events.discardVersionsWithOldKeys();
        events.normalizeUsers();

        System.out.println("Events loaded");

        final List<Events> sessions = events.splitIntoSessions();

        final Set<Events> sessionCorrection = new HashSet<>();
        sessions.sort(new Comparator<Events>() {
            @Override
            public int compare(Events o1, Events o2) {
                final int cmp = compareKeyTypeRound(o1, o2);
                if (cmp != 0) {
                    return cmp;
                }
                return o1.minTime().compareTo(o2.minTime());
            }
        });
        Events lastSession = null;
        boolean hadEnd = true;
        for (int i = 0; i < sessions.size(); i++) {
            final Events session = sessions.get(i);
            if (!session.getExample().getSessionType().equals("R")) {
                continue;
            }
            //the first session in each review round has a wrong round number that needs to be adjusted
            //  it is wrongly numbered as the last session of the previous round
            //  this correction still does not work fully, for example when the ticket's state was
            //  changed outside the review tool
            if (hadEnd || !session.getExample().getTicketKey().equals(lastSession.getExample().getTicketKey())) {
                sessionCorrection.add(session);
            }
            hadEnd = session.filter(e -> e.getDataType().equals("reviewEnded"))
                .filter(e -> !e.getProperty("endTransition").get().equalsIgnoreCase("pause"))
                .iterator().hasNext();
            lastSession = session;
        }

        try (SqlWriter<Events> ws = new SqlWriter<>(new File("sessions.sql"), "sessions");
                SqlWriter<Event> we = new SqlWriter<>(new File("events.sql"), "events");
                SqlWriter<EventProperty> wp = new SqlWriter<>(new File("properties.sql"), "properties")) {
            ws.addColumn("key", "VARCHAR(256)", e -> e.getExample().getTicketKey());
            ws.addColumn("type", "VARCHAR(256)", e -> e.getExample().getSessionType());
            ws.addColumn("round", "INTEGER", e -> e.getExample().getReviewRound() + (sessionCorrection.contains(e) ? 1 : 0));
            ws.addKeyColumn("session", "VARCHAR(256)", e -> e.getExample().getSessionId());
            ws.addKeyColumn("user", "VARCHAR(256)", e -> e.getExample().getUser());
            ws.addColumn("startTime", "DATETIME", e -> e.minTime());
            ws.addColumn("endTime", "DATETIME", e -> e.maxTime());

            we.addKeyColumn("user", "VARCHAR(256)", e -> e.getUser());
            we.addKeyColumn("time", "BIGINT", e -> e.getTimestamp().toEpochMilli());
            we.addColumn("session", "VARCHAR(256)", e -> e.getSessionId());
            we.addColumn("tool", "VARCHAR(256)", e -> e.getTool());
            we.addColumn("eventType", "VARCHAR(256)", e -> e.getDataType());

            wp.addKeyColumn("user", "VARCHAR(256)", e -> e.event.getUser());
            wp.addKeyColumn("time", "BIGINT", e -> e.event.getTimestamp().toEpochMilli());
            wp.addKeyColumn("name", "VARCHAR(256)", e -> e.name);
            wp.addColumn("value", "VARCHAR(256)", e -> e.event.getProperty(e.name).get());

            for (final Events eventsForSession : sessions) {
                ws.writeRow(eventsForSession);

                for (final Event e : eventsForSession) {
                    we.writeRow(e);

                    for (final String name : e.getProperties().keySet()) {
                        wp.writeRow(new EventProperty(e, name));
                    }
                }
            }
        }

        System.out.println("Records written, session count=" + sessions.size());
    }

    private static int compareKeyTypeRound(Events es1, Events es2) {
        final Event e1 = es1.getExample();
        final Event e2 = es2.getExample();

        final int cmp1 = e1.getTicketKey().compareTo(e2.getTicketKey());
        if (cmp1 != 0) {
            return cmp1;
        }

        final int cmp2 = e1.getSessionType().compareTo(e2.getSessionType());
        if (cmp2 != 0) {
            return cmp2;
        }

        return e1.getReviewRound().compareTo(e2.getReviewRound());
    }

}
