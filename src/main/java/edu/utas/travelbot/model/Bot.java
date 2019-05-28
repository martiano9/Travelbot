package edu.utas.travelbot.model;

import com.google.maps.model.PlaceDetails;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.maps.GeoApiContext;
import com.google.maps.PlacesApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlaceType;
import com.google.maps.model.PlacesSearchResult;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.*;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.J48;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

import edu.stanford.nlp.util.CoreMap;
import edu.utas.travelbot.repository.CategoryDAO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import weka.core.Instances;
import weka.core.converters.ArffLoader;

public class Bot {
    public double lat;
    public double lon;

    private String[] cuisines = new String[]{"Vietnamese", "Chinese", "Thai", "Japanese"};
    private StanfordCoreNLP pipeline;
    private CategoryDAO categoryDAO;

    private SimpMessagingTemplate messagingTemplate;
    private String username;
    GeoApiContext context;

    // Intent
    private String lastCategory = "";
    private String posipleNextCategory = "none";
    private int searchingRadius = 2000;

    // User information
    private String name = null;
    private boolean userFromHome;
    private int userBudget;
    private int userWaiting;
    PlacesSearchResult[] results;
    Evaluation j48Evaluation;
    Classifier classifier;

    public Bot() {
        // Init J48 Decision tree
        Instances trainingDataSet = getDataSet("res_pattern.arff");
        this.classifier = new J48();
        try {
            this.j48Evaluation = new Evaluation(trainingDataSet);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // set up pipeline properties
        Properties props = new Properties();
        // set the list of annotators to run
        props.setProperty("annotators", "tokenize,ssplit,pos,parse,lemma,ner,sentiment");
        // set a property for an annotator, in this case the coref annotator is being set to use the neural algorithm
        props.setProperty("coref.algorithm", "neural");
        props.put("ner.combinationMode", "HIGH_RECALL");
        //  build pipeline
        pipeline = new StanfordCoreNLP(props);
        categoryDAO = new CategoryDAO();
        context = new GeoApiContext.Builder()
                .apiKey("AIzaSyAVxM30XgvxktwAKHZGZ2_jyMy61SUUL4U")
                .build();
    }

    /**
     * This method is to load the data set.
     * @param fileName
     * @return
     * @throws IOException
     */
    public Instances getDataSet(String fileName) {
        try {
            /**
             * we can set the file i.e., loader.setFile("finename") to load the data
             */
            int classIdx = 1;
            /** the arffloader to load the arff file */
            ArffLoader loader = new ArffLoader();
            /** load the traing data */
            loader.setSource(new File("categorizer.txt"));
            /**
             * we can also set the file like loader3.setFile(new
             * File("test-confused.arff"));
             */
            //loader.setFile(new File(fileName));
            Instances dataSet = loader.getDataSet();
            /** set the index based on the data given in the arff files */
            dataSet.setClassIndex(classIdx);
            return dataSet;
        } catch (IOException e) {

        }
        return null;
    }

    public ChatMessage ask(String question, SimpMessagingTemplate messagingTemplate, String username) {
        // Update private
        this.messagingTemplate = messagingTemplate;
        this.username = username;

        // Show loading on client
        loading();

        ChatMessage data = new ChatMessage();
        Random r = new Random();

        data.setSender("BOT");
        data.setType(ChatMessage.MessageType.REPLY);
        data.setContent(categoryDAO.getAnswer("none"));

        // Use NLP to analyze the question
        CoreDocument document = new CoreDocument(question);
        pipeline.annotate(document);

        // list of the part-of-speech tags for the second sentence
        CoreSentence sentence = document.sentences().get(0);
        List<String> posTags = sentence.posTags();
        log("pos tags: " + posTags.toString());

        // NER
        List<String> nerTags = sentence.nerTags();
        log("ner tags: " + nerTags.toString());

        // Entity mention
        List<CoreEntityMention> entityMentions = sentence.entityMentions();
        log("entity mentions: " + entityMentions.toString());

        // Lemm
        List<CoreLabel> tokens = sentence.tokens();
        List<String> lemmas = new LinkedList<String>();
        for (CoreLabel token: tokens) {
            // Retrieve and add the lemma for each word into the list of lemmas
            lemmas.add(token.get(CoreAnnotations.LemmaAnnotation.class));
        }
        log("lemmas: " + lemmas.toString());

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
        log("sentiment: " + sentimentStr + "(" + mainSentiment + ")");

        // Detect category
        String category = categoryDAO.detectCategory(lemmas);
        log("intent: " + category);

        // Scenario classifying
        if (category.equals("greeting") && lastCategory.equals("")) {
            chat(categoryDAO.getAnswer("greeting"));
            finish();
        } else if (category.equals("restaurant-search") && lastCategory.equals("")) {
                this.results = searchRestaurant("");
                log("result found: " + this.results.length);

                // hide loading on client
                finish();

                // Send message to client
                data.setContent(MessageFormat.format("I found the closet {0} restaurants near your location.", results.length, searchingRadius/1000));
                messagingTemplate.convertAndSendToUser(username, "/chat", data);

                // Send result to client
                result(this.results);

                // Update intention
                lastCategory = category;

                // Prepare next script
                prepareNextIntent(category);

        } else if (category.equals("restaurant-type")  && posipleNextCategory.equals("restaurant-type")) {
            String cuisine = getNERTag("NATIONALITY", nerTags, lemmas);
            // follow dialog scenario
            if (posipleNextCategory.equals(category)) {

                // Search restaurant
                this.results = searchRestaurant(question);
                log("result found: " + this.results.length);

                // hide loading on client
                finish();

                // Send message to client
                chat(MessageFormat.format("I found the closet {0} {1} restaurants near your location.", results.length, cuisine));

                // Send result to client
                result(this.results);

                lastCategory = "restaurant-type";
                posipleNextCategory = "restaurant-more";
                log("next-intent: " + posipleNextCategory);
            } else {
                String mess = MessageFormat.format("Do you mean {0} restaurants?", cuisine);
                chat(mess);

                // hide loading on client
                finish();
            }
        } else if (posipleNextCategory.equals("restaurant-price")) {
            String answer = getNERTag("NUMBER", nerTags, lemmas);
            this.userBudget = wordtonum(answer);

            // Prepare next script
            prepareNextIntent("restaurant-price");
            finish();
        } else if (posipleNextCategory.equals("restaurant-from")) {
            this.userFromHome = question.toLowerCase().contains("yes") || question.toLowerCase().contains("true");

            // Prepare next script
            prepareNextIntent("restaurant-from");
            finish();
        } else if (posipleNextCategory.equals("restaurant-waiting")) {
            String answer = getNERTag("NUMBER", nerTags, lemmas);
            this.userWaiting = wordtonum(answer);

            // J48 Prediction
            String predictionType = j48Prediction();

            // Show results
            PlacesSearchResult[] results = searchRestaurant("");
            assert results != null;
            log("result found: " + results.length);

            // hide loading on client
            finish();

            // Send message to client
            data.setContent(MessageFormat.format("I found the closet {0} <b>{1}</b> restaurants near your location.", results.length, predictionType));
            messagingTemplate.convertAndSendToUser(username, "/chat", data);

            // Send result to client
            result(results);

            // Prepare next script
            prepareNextIntent("restaurant-result");
            finish();
        } else if (posipleNextCategory.equals("restaurant-satisfy")) {
            if (question.toLowerCase().equals("no") || mainSentiment < 0) {
                prepareNextIntent("restaurant-input");
            } else {
                prepareNextIntent("restaurant-input-yes");
            }
            finish();
        } else if (category.equals("conversation-complete") && posipleNextCategory.equals("restaurant-more")) {
            // hide loading on client
            finish();
            chat("Anytime! Let me know if you need anything else.");
            posipleNextCategory = "conversation-end";
        } else if (category.equals("conversation-end") && posipleNextCategory.equals("conversation-end")) {
            // hide loading on client
            finish();
            prepareNextIntent("conversation-end");
        } else if (posipleNextCategory.equals("restaurant-more")) {
            String ordinal = getNERTag("ORDINAL", nerTags, lemmas);
            int idx = this.ordinalToNum(ordinal) - 1;
            chat(MessageFormat.format("Here’s the information and direction that I found for <b>{0}</b> restaurant.", results[idx].name));

            // Get detail information
            PlaceDetails place = getPlaceDetail(results[idx].placeId);
            assert place != null;
            sendDetail(place);

            finish();
        } else if (posipleNextCategory.equals("take-feedback")) {
            if (mainSentiment >= 0) {
                chat("Thank you for using our service. Have a good day!");
            } else {
                chat("Thank you for your suggestion. My creator will look into this. See you. Have a great day.");
            }

            // reset bot
            posipleNextCategory = "";
            lastCategory = "";
            userWaiting = -1;
            userFromHome = false;
            userBudget = -1;

            finish();
        } else if (category.equals("conversation-continue")) {
            // hide loading on client
            finish();

            data.setContent(categoryDAO.getAnswer(category));
            messagingTemplate.convertAndSendToUser(username, "/chat", data);
        } else {
            // hide loading on client
            finish();

            // reset bot
            posipleNextCategory = "";
            lastCategory = "";
            userWaiting = -1;
            userFromHome = false;
            userBudget = -1;

            data.setContent(categoryDAO.getAnswer(category));
            messagingTemplate.convertAndSendToUser(username, "/chat", data);
        }

        // End of NLP Analysis
        log("-");

        return data;
    }

    private String j48Prediction() {
        return cuisines[(new Random()).nextInt(cuisines.length)];
    }

    private PlacesSearchResult[] searchRestaurant(String key) {
        try {
            LatLng location = new LatLng();
            location.lat = lat;
            location.lng = lon;

           return PlacesApi.textSearchQuery(context, key)
                    .type(PlaceType.RESTAURANT)
                    .location(location)
                    .radius(searchingRadius)
                    .await()
                    .results;
        } catch (ApiException | InterruptedException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private PlaceDetails getPlaceDetail(String placeId) {
        try {
            return PlacesApi.placeDetails(context, placeId).await();
        } catch (ApiException | InterruptedException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void prepareNextIntent(String currentIntent) {
        lastCategory = currentIntent;
        switch (currentIntent) {
            case "restaurant-search":
                posipleNextCategory = "restaurant-price";
                break;
            case "restaurant-price":
                posipleNextCategory = "restaurant-from";
                break;
            case "restaurant-from":
                posipleNextCategory = "restaurant-waiting";
                break;
            case "restaurant-result":
                posipleNextCategory = "restaurant-satisfy";
                break;
            case "restaurant-input":
                posipleNextCategory = "restaurant-type";
                break;
            case "restaurant-input-yes":
                posipleNextCategory = "restaurant-more";
                break;
            case "conversation-end":
                posipleNextCategory = "take-feedback";
                break;
        }

        log("next-intent: " + posipleNextCategory);
        chat(categoryDAO.getAnswer(posipleNextCategory));
    }

    private void chat(String message) {
        ChatMessage data = new ChatMessage();
        data.setSender("BOT");
        data.setType(ChatMessage.MessageType.REPLY);
        data.setContent(message);

        messagingTemplate.convertAndSendToUser(username, "/chat", data);
    }

    private void sendDetail(PlaceDetails place) {
        ChatMessage data = new ChatMessage();
        data.setSender("BOT");
        data.setType(ChatMessage.MessageType.DETAIL);

        JSONObject obj = new JSONObject();
        obj.put("name", place.name);
        obj.put("phone", place.formattedPhoneNumber);
        obj.put("address", place.formattedAddress);
        obj.put("rating", place.rating);
        obj.put("lat", place.geometry.location.lat);
        obj.put("lng", place.geometry.location.lng);
        if (place.priceLevel != null) {
            obj.put("price", place.priceLevel.toString());
        }
        if (place.photos != null && place.photos.length > 0) {
            obj.put("photo", place.photos[0].photoReference);
        }
        if (place.openingHours != null) {
            obj.put("opening", place.openingHours.openNow);
        }
        data.setContent(obj.toString());


        messagingTemplate.convertAndSendToUser(username, "/chat", data);
    }

    private void result(PlacesSearchResult[] result) {
        ChatMessage data = new ChatMessage();
        data.setSender("BOT");
        data.setType(ChatMessage.MessageType.RESULT);

        JSONArray ja = new JSONArray();
        for (PlacesSearchResult r : result) {
            JSONObject obj = new JSONObject();
            obj.put("name", r.name);
            obj.put("address", r.formattedAddress);
            obj.put("rating", r.rating);
            obj.put("lat", r.geometry.location.lat);
            obj.put("lng", r.geometry.location.lng);
            if (r.photos != null && r.photos.length > 0) {
                obj.put("photo", r.photos[0].photoReference);
            }
            if (r.openingHours != null) {
                obj.put("opening", r.openingHours.openNow);
            }
            ja.put(obj);
        }
        data.setContent(ja.toString());

        messagingTemplate.convertAndSendToUser(username, "/chat", data);
    }

    private void log(String message) {
        ChatMessage data = new ChatMessage();
        data.setSender("BOT");
        data.setType(ChatMessage.MessageType.REPLY);
        data.setContent(message);

        messagingTemplate.convertAndSendToUser(username, "/log", data);
    }

    private void loading() {
        ChatMessage data = new ChatMessage();
        data.setSender("BOT");
        data.setType(ChatMessage.MessageType.LOADING);
        data.setContent("add_loading");

        messagingTemplate.convertAndSendToUser(username, "/chat", data);
    }

    private void finish() {
        ChatMessage data = new ChatMessage();
        data.setSender("BOT");
        data.setType(ChatMessage.MessageType.FINISH);
        data.setContent("remove_loading");

        messagingTemplate.convertAndSendToUser(username, "/chat", data);
    }

    private String getNERTag(String tagName, List<String> ners, List<String> tokens) {
        for (int i = 0; i < ners.size(); i++) {
            if (ners.get(i).equals(tagName)) {
                return tokens.get(i);
            }
        }
        return "";
    }

    private int wordtonum(String word)
    {
        int num = 0;
        try {
            num = Integer.parseInt(word);
        } catch (NumberFormatException ex) {
            switch (word) {
                case "one":  num = 1;
                    break;
                case "two":  num = 2;
                    break;
                case "three":  num = 3;
                    break;
                case "four":  num = 4;
                    break;
                case "five":  num = 5;
                    break;
                case "six":  num = 6;
                    break;
                case "seven":  num = 7;
                    break;
                case "eight":  num = 8;
                    break;
                case "nine":  num = 9;
                    break;
                default:
                    break;
           /*default: num = "Invalid month";
                             break;*/
            }
        }

        return num;
    }

    private int ordinalToNum(String ordinal) {
        int num = 1;
        switch (ordinal.toLowerCase()) {
            case "first": num = 1;
                break;
            case "second": num = 2;
                break;
            case "third": num = 3;
                break;
            case "forth": num = 4;
                break;
            case "fifth": num = 5;
                break;
            case "sixth": num = 6;
                break;
            case "seventh": num = 7;
                break;
            default:
                break;
        }
        return num;
    }
}
