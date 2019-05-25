package edu.utas.travelbot.model;

public class ChatMessage {
    private MessageType type;
    private String content;
    private String sender;
    private String extraData;
    private String stemmedQuestion;

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE,
        REPLY,
        QUERY,
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getExtraData() {
        return extraData;
    }

    public void setExtraData(String extraData) {
        this.extraData = extraData;
    }

    public String getStemmedQuestion() {
        return stemmedQuestion;
    }

    public void setStemmedQuestion(String str) {
        this.stemmedQuestion = str;
    }
}
