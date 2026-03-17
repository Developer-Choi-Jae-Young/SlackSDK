package co.acta.slacksdk.interfaces;

public interface SlackMessageable {
    String getSDKParentBoardId();
    String getSDKBoardId();
    String getSDKContent();
    String getSDKWriter();
    String getSDKRegDate();
    default String getSDKTitle() { return ""; }
    default String getSDKReplyId() { return getSDKBoardId(); }
}
