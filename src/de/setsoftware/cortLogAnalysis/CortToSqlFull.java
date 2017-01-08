package de.setsoftware.cortLogAnalysis;

import java.io.File;
import java.util.List;

import de.setsoftware.cortLogAnalysis.model.Event;
import de.setsoftware.cortLogAnalysis.model.Events;

public class CortToSqlFull {

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

        try (SqlWriter<Events> ws = new SqlWriter<>(new File("sessions.sql"), "sessions");
                SqlWriter<Event> we = new SqlWriter<>(new File("events.sql"), "events");
                SqlWriter<EventProperty> wp = new SqlWriter<>(new File("properties.sql"), "properties")) {
            ws.addColumn("key", "VARCHAR(256)", e -> e.getExample().getTicketKey());
            ws.addColumn("type", "VARCHAR(256)", e -> e.getExample().getSessionType());
            ws.addColumn("round", "INTEGER", e -> e.getCorrectedRound());
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

}
