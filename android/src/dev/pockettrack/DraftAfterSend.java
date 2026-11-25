package dev.pockettrack;

/** Remove only the acknowledged, unchanged prefix; preserve edits made during a send. */
public final class DraftAfterSend {
    public static int prefixLength(String sent,String current){return !sent.isEmpty() && current.startsWith(sent)?sent.length():0;}
}
