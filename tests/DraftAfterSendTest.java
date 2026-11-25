import dev.pockettrack.DraftAfterSend;

public final class DraftAfterSendTest {
    private static void check(String sent,String current,String expected){
        String remaining=current.substring(DraftAfterSend.prefixLength(sent,current));
        if(!remaining.equals(expected))throw new AssertionError("Draft retention failed");
    }
    public static void main(String[] args){
        check("第一句","第一句","");
        check("第一句","第一句第二句","第二句");
        check("原句","修改后的原句","修改后的原句");
        check("完整句","完整","完整");
        check("已发😀","已发😀下一句🙂","下一句🙂");
        check("","新内容","新内容");
        check("已删除","","");
        System.out.println("DraftAfterSend: 7 cases passed");
    }
}
