package de.setsoftware.furtherAnalysis.navigation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class NavigationSession {

    private static final String JUMPED_TO = "jumpedTo";
    private static final String ACTIVE_FILES_CHANGED = "activeFilesChanged";

    private enum FileNavType {
        JUMP_TREE,
        JUMP_NEXT,
        OTHER
    }

    private static final class FileNavEvent {
        private final String file;
        private final FileNavType type;

        public FileNavEvent(String file, FileNavType type) {
            this.type = type;
            this.file = file;
        }

        public boolean isToolNext() {
            return this.type == FileNavType.JUMP_NEXT;
        }

        public boolean isDirectlyAfter(int i, NavigationSession s) {
            return this.type != FileNavType.JUMP_NEXT && s.alphabeticIndex(this) == i + 1;
        }

        public boolean isSkipAfter(int i, NavigationSession s) {
            final int ownIndex = s.alphabeticIndex(this);
            return this.type != FileNavType.JUMP_NEXT && ownIndex > i + 1 && s.allNonVisited(i + 1, ownIndex);
        }
    }

    private final SvnChangesets svn;
    private final String ticketKey;
    private final String user;
    private final String session;
    private final List<FileNavEvent> events;

    private boolean firstEvent = true;
    private String curFile;
    private String expectedFromJump;
    private FileNavType jumpType;


    public NavigationSession(SvnChangesets svn, String ticketKey, String user, String session) {
        this.svn = svn;
        this.ticketKey = ticketKey;
        this.user = user;
        this.session = session;
        this.events = new ArrayList<>();
    }

    public void addEvent(String eventType, String files, String jumpType) {
        final String fileNorm = this.normalizeFile(files);
        if (this.firstEvent) {
            this.firstEvent = false;
            //ignore the first activeFilesChanged event, it is most likely only the
            //  files that were open before the review
            final Set<String> toVisit = this.svn.get(this.ticketKey);
            final boolean isToVisit = toVisit == null || toVisit.contains(fileNorm);
            if (eventType.equals(ACTIVE_FILES_CHANGED) && !isToVisit) {
                return;
            }
        }
        if (eventType.equals(ACTIVE_FILES_CHANGED) && files.equals("[]")) {
            //ignore empty file sets, they are due to the user closing all editors, which is not a navigation
            return;
        }
        if (eventType.equals(JUMPED_TO)) {
            if (jumpType.equals("tree")) {
                this.expectedFromJump = fileNorm;
                this.jumpType = FileNavType.JUMP_TREE;
            } else if (jumpType.equals("next")) {
                this.expectedFromJump = fileNorm;
                this.jumpType = FileNavType.JUMP_NEXT;
            }
        } else {
            if (fileNorm.equals(this.curFile)) {
                //ignore in file navigation
                this.expectedFromJump = null;
                return;
            }
            if (fileNorm.equals(this.expectedFromJump)) {
                this.events.add(new FileNavEvent(fileNorm, this.jumpType));
            } else {
                this.events.add(new FileNavEvent(fileNorm, FileNavType.OTHER));
            }
            this.expectedFromJump = null;
            this.curFile = fileNorm;
        }
    }

    private String normalizeFile(String files) {
        if (files.startsWith("[") && files.endsWith("]")) {
            if (files.contains(", ")) {
                //list of files. take the first one
                return this.normalizeFile(files.substring(1, files.indexOf(", ")));
            }
            return this.normalizeFile(files.substring(1, files.length() - 1));
        } else {
            String normSlashes = files.replace("\\", "/");
            normSlashes = this.removePrefixUntil(normSlashes, "/WorkspacePosy/");
            normSlashes = this.removePrefixUntil(normSlashes, "/Workspace/");
            if (normSlashes.contains("/Workspace") && !normSlashes.contains("WorkspaceTestcaseExecutor.java")) {
                throw new RuntimeException("potential problem " + normSlashes);
            }
            return normSlashes;
        }
    }

    private String removePrefixUntil(String normSlashes, String string) {
        final int index = normSlashes.indexOf(string);
        if (index < 0) {
            return normSlashes;
        }
        return normSlashes.substring(index + string.length());
    }

    public static void printHeader() {
        System.out.println("user;"
                        + "session;"
                        + "checkedFileCount;"
                        + "extraFileCount;"
                        + "nonVisitedFileCount;"
                        + "fileNavCountTotal;"
                        + "startToolNext;"
                        + "startFirst;"
                        + "startSkip;"
                        + "startOther;"
                        + "furtherToolNext;"
                        + "furtherDirectAfter;"
                        + "furtherSkip;"
                        + "furtherOther");
    }

    public void print() {
        final Set<String> visited = this.getVisitedFiles();
        final List<String> toReview = this.getFilesInToolOrder();
        final Set<String> checked = new HashSet<>(visited);
        checked.retainAll(toReview);
        final Set<String> extra = new HashSet<>(visited);
        extra.removeAll(toReview);
        final Set<String> nonVisited = new HashSet<>(toReview);
        nonVisited.removeAll(visited);

        System.out.println(String.format("%s;%s;%d;%d;%d;%d;%d;%d;%d;%d;%d;%d;%d;%d",
                        this.user,
                        this.session,
                        checked.size(),
                        extra.size(),
                        nonVisited.size(),
                        this.events.size(),
                        this.getStartToolNext(),
                        this.getStartFirst(),
                        this.getStartSkip(),
                        this.getStartOther(),
                        this.getFurtherToolNext(),
                        this.getFurtherDirectAfter(),
                        this.getFurtherSkip(),
                        this.getFurtherOther()));
    }

    /**
     * The user pressed "jump to next" to get to the first stop.
     */
    private int getStartToolNext() {
        if (this.events.isEmpty()) {
            return 0;
        }
        return this.events.get(0).isToolNext() ? 1 : 0;
    }

    /**
     * The user started with the first stop in the tree, but did not use jump next.
     */
    private int getStartFirst() {
        if (this.events.isEmpty()) {
            return 0;
        }
        final FileNavEvent ev = this.events.get(0);
        return ev.isDirectlyAfter(-1, this) ? 1 : 0;
    }

    /**
     * The user selected another stop as a first and never visited the earlier stops, so he
     * probably decided to skip them.
     */
    private int getStartSkip() {
        if (this.events.isEmpty()) {
            return 0;
        }
        final FileNavEvent ev = this.events.get(0);
        return ev.isSkipAfter(-1, this) ? 1 : 0;
    }

    private boolean allNonVisited(int startIndexIncl, int endIndexExcl) {
        final Set<String> visitedFiles = this.getVisitedFiles();
        final List<String> filesInOrder = this.getFilesInToolOrder();
        for (int i =  startIndexIncl; i < endIndexExcl; i++) {
            if (visitedFiles.contains(filesInOrder.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The user did something else to get to the first stop (i.e. he explicitly chose to start
     * with a file that was not the first, but later also reviewed the first).
     */
    private int getStartOther() {
        if (this.events.isEmpty()) {
            return 0;
        }
        return 1 - this.getStartToolNext() - this.getStartFirst() - this.getStartSkip();
    }

    private int getFurtherToolNext() {
        int ret = 0;
        for (int i = 1; i < this.events.size(); i++) {
            if (this.events.get(i).isToolNext()) {
                ret++;
            }
        }
        return ret;
    }

    private int getFurtherDirectAfter() {
        if (this.events.isEmpty()) {
            return 0;
        }
        int ret = 0;
        int prevIndex = this.alphabeticIndex(this.events.get(0));
        for (int i = 1; i < this.events.size(); i++) {
            final FileNavEvent event = this.events.get(i);
            if (event.isDirectlyAfter(prevIndex, this)) {
                ret++;
            }
            prevIndex = this.alphabeticIndex(event);
        }
        return ret;
    }

    private int getFurtherSkip() {
        if (this.events.isEmpty()) {
            return 0;
        }
        int ret = 0;
        int prevIndex = this.alphabeticIndex(this.events.get(0));
        for (int i = 1; i < this.events.size(); i++) {
            final FileNavEvent event = this.events.get(i);
            if (event.isSkipAfter(prevIndex, this)) {
                ret++;
            }
            prevIndex = this.alphabeticIndex(event);
        }
        return ret;
    }

    private int getFurtherOther() {
        if (this.events.isEmpty()) {
            return 0;
        }
        int ret = 0;
        int prevIndex = this.alphabeticIndex(this.events.get(0));
        for (int i = 1; i < this.events.size(); i++) {
            final FileNavEvent event = this.events.get(i);
            if (!(event.isToolNext() || event.isDirectlyAfter(prevIndex, this) || event.isSkipAfter(prevIndex, this))) {
                ret++;
            }
            prevIndex = this.alphabeticIndex(event);
        }
        return ret;
    }

    private int alphabeticIndex(FileNavEvent ev) {
        return this.getFilesInToolOrder().indexOf(ev.file);
    }

    private List<String> getFilesInToolOrder() {
        final TreeSet<String> changesetFiles = this.svn.get(this.ticketKey);
        if (changesetFiles == null) {
            //if no data is available, fall back to the visited files
            return new ArrayList<>(this.getVisitedFiles());
        } else {
            return new ArrayList<>(changesetFiles);
        }
    }

    private TreeSet<String> getVisitedFiles() {
        final TreeSet<String> files = new TreeSet<>();
        for (final FileNavEvent e : this.events) {
            files.add(e.file);
        }
        return files;
    }

}
