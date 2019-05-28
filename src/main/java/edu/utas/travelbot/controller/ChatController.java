package edu.utas.travelbot.controller;

import edu.utas.travelbot.model.Bot;
import edu.utas.travelbot.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    private SimpMessagingTemplate messagingTemplate;
    private Bot bot;

    @Autowired
    public ChatController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.bot = new Bot();
    }

    @MessageMapping("/greeting")
    public void greeting(Message<Object> message, @Payload String payload, Principal principal) throws Exception {
        String username = principal.getName();
        logger.info("new registration: username="+username+", payload="+payload);

        // Init payload data
        ChatMessage data = new ChatMessage();
        data.setContent("Hi there! \n" +
                "My name is Brian. I am here to help you with your travel plans. What would you like to ask today?");
        data.setSender("BOT");
        data.setType(ChatMessage.MessageType.REPLY);

        // Send message to client
        messagingTemplate.convertAndSendToUser(username, "/chat", data);
    }

    @MessageMapping("/chat")
    public void chat(Message<Object> message, @Payload String payload, Principal principal) throws Exception {
        String username = principal.getName();

        // Get lat, lon from header and notify the bot
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String lat = accessor.getFirstNativeHeader("lat");
        String lon = accessor.getFirstNativeHeader("long");
        bot.lat = new Double(lat);
        bot.lon = new Double(lon);

        logger.info("new registration: username="+username+", payload="+payload);

        // Init payload data
        ChatMessage data = new ChatMessage();
        data.setContent(payload);
        data.setSender("USER");
        data.setType(ChatMessage.MessageType.QUERY);

        // Send message to client
        messagingTemplate.convertAndSendToUser(username, "/chat", data);

        // Init payload data
        bot.ask(payload, messagingTemplate, username);
    }
}
