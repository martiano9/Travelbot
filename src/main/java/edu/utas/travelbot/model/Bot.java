package edu.utas.travelbot.model;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ie.util.*;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.*;
import java.util.*;

import edu.stanford.nlp.util.CoreMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;

public class Bot {
    private int scenario = -1;
    private int lastScenario = -1;
    private StanfordCoreNLP pipeline;

    public Bot() {
        // set up pipeline properties
        Properties props = new Properties();
        // set the list of annotators to run
        props.setProperty("annotators", "tokenize,ssplit,pos,parse,lemma,ner,sentiment");
        // set a property for an annotator, in this case the coref annotator is being set to use the neural algorithm
//        props.setProperty("coref.algorithm", "neural");
        //  build pipeline
        pipeline = new StanfordCoreNLP(props);
    }

    public ChatMessage ask(String question, SimpMessagingTemplate messagingTemplate, String username) {
        ChatMessage data = new ChatMessage();
        data.setSender("BOT");
        data.setType(ChatMessage.MessageType.REPLY);
        data.setContent("Sorry, I don't understand your question.");

        // Use NLP to analyze the question
        CoreDocument document = new CoreDocument(question);
        pipeline.annotate(document);

        // list of the part-of-speech tags for the second sentence
        CoreSentence sentence = document.sentences().get(0);
        List<String> posTags = sentence.posTags();
        log("pos tags: " + posTags.toString(), messagingTemplate, username);

        // NER
        List<String> nerTags = sentence.nerTags();
        log("ner tags: " + nerTags.toString(), messagingTemplate, username);

        // NER
        List<CoreEntityMention> entityMentions = sentence.entityMentions();
        log("entity mentions: " + entityMentions.toString(), messagingTemplate, username);

        // Lemm
        List<CoreLabel> tokens = sentence.tokens();
        List<String> lemmas = new LinkedList<String>();
        for (CoreLabel token: tokens) {
            // Retrieve and add the lemma for each word into the list of lemmas
            lemmas.add(token.get(CoreAnnotations.LemmaAnnotation.class));
        }
        log("lemmas: " + lemmas.toString(), messagingTemplate, username);

        // Sentiment
        int longest = 0;
        int mainSentiment = 0;
        String sentimentStr = null;
        Annotation annotation = pipeline.process(question);
        List<CoreMap> maps = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        for (CoreMap map : maps) {
            Tree sentimentTree = map.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);

            int sentiment = RNNCoreAnnotations.getPredictedClass(sentimentTree) - 2;
            String partText = map.toString();
            if (partText.length() > longest) {
                mainSentiment = sentiment;
                sentimentStr = map.get(SentimentCoreAnnotations.SentimentClass.class);

                longest = partText.length();
            }
        }
        log("sentiment: " + sentimentStr + "(" + mainSentiment + ")", messagingTemplate, username);

        // End of NLP Analysis
        log("-", messagingTemplate, username);


        String analyzedQuestion = question;
        String lower = analyzedQuestion.toLowerCase();

        // 4. Predict scenario
        if (lastScenario == -1){
            // 0
            if (lower.equals("hi")
                    || lower.equals("hello")
                    || lower.equals("good morning")) {
                data.setContent("Hello");
            }

            // 1
            if (lower.equals("how are you")
                    || lower.equals("how are you today")
                    || lower.equals("how are you doing")) {
                data.setContent("Very well. Thank you.\nHow are you doing today?");
                lastScenario = 1;
            }

            // 2
            if (lower.equals("show me my location")
                    || lower.equals("show my location")
                    || lower.equals("current location")) {
                data.setContent("Here's your current location");
                data.setExtraData("current-location");
            }

            // 3
            if (lemmas.contains("look")
                    || lemmas.contains("search")
                    || lemmas.contains("restaurant")
                    || lemmas.contains("hotel")
                    || lemmas.contains("place")
                    || lemmas.contains("find")) {
                if (lemmas.contains("restaurant")) {
                    data.setContent("I'm picking the best from the list for you. Visit these wonderful restaurants around your area");
                    data.setExtraData("show-restaurants");
                }
                if (lemmas.contains("hotel")) {
                    data.setContent("I'm picking the best from the list for you. Visit these wonderful hotels around your area");
                    data.setExtraData("show-hotels");
                }
                if (lemmas.contains("place")) {
                    data.setContent("I'm picking the best from the list for you. Visit these wonderful spots around your area");
                    data.setExtraData("show-places");
                }
                lastScenario = 3;
            }

            //4 recommend me some res within 10kms
        }

        if (lastScenario == 1) {
            if (question.toLowerCase().equals("i'm good") || question.toLowerCase().equals("very well")) {
                data.setContent("Great! May I help you?");
                lastScenario = -1;
            }
        } else if (lastScenario == 3) {

        } else {

        }

        // Send message to client
        messagingTemplate.convertAndSendToUser(username, "/chat", data);
        return data;
    }

    private void log(String message, SimpMessagingTemplate messagingTemplate, String username) {
        ChatMessage data = new ChatMessage();
        data.setSender("BOT");
        data.setType(ChatMessage.MessageType.REPLY);
        data.setContent(message);

        messagingTemplate.convertAndSendToUser(username, "/log", data);
    }
}
