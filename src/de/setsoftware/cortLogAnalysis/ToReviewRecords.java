package de.setsoftware.cortLogAnalysis;

import java.io.File;
import java.util.List;

import de.setsoftware.cortLogAnalysis.model.Event;
import de.setsoftware.cortLogAnalysis.model.EventTypes;
import de.setsoftware.cortLogAnalysis.model.Events;

public class ToReviewRecords {

    public static void main(final String[] args) throws Exception {
        final Events events = Events.loadDefault();
        events.normalizeUsers();

        System.out.println("Events loaded");

        final List<Events> sessions = events.splitIntoSessions();
        try (SqlWriter<Events> w = new SqlWriter<>(new File("cortsessions.script"), "cortdata")) {
            w.addColumn("key", "VARCHAR(256)", e -> e.getExample().getResource());
            w.addColumn("user", "VARCHAR(256)", e -> e.getExample().getUser());
            w.addColumn("round", "INTEGER", e -> e.getRoundFromFirstSessionStart());
            w.addColumn("type", "VARCHAR(256)", e -> e.getTypeOfFirstSession());
            w.addColumn("startTime", "TIMESTAMP", e -> e.minTime());
            w.addColumn("endTime", "TIMESTAMP", e -> e.maxTime());
            w.addColumn("tool", "VARCHAR(256)", e -> e.getToolFromFirstEvent());
            w.addColumn("toolCount", "INTEGER", e -> e.mapToSet(Event::getTool).size());
            w.addColumn("eventCount", "INTEGER", e -> e.size());
            w.addColumn("launchCount", "INTEGER", e -> e.countEventType(EventTypes.LAUNCH));
            w.addColumn("changeCount", "INTEGER", e -> e.countEventType(EventTypes.FILE_CHANGED));

            for (final Events eventsForSession : sessions) {
                w.writeRow(eventsForSession);
            }
        }

        System.out.println("Records written, session count=" + sessions.size());
    }

}
