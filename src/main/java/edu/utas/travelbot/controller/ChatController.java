package edu.utas.travelbot.controller;

import java.util.HashMap;
import java.util.Map;
import edu.utas.travelbot.model.Bot;
import edu.utas.travelbot.model.ChatMessage;
import edu.utas.travelbot.service.DatasetService;
import edu.utas.travelbot.service.ExecuteGA;
import edu.utas.travelbot.service.NeuralNetworkService;
import edu.utas.travelbot.service.ProcessResponse;
import edu.utas.travelbot.utils.Stemmer;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSet;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Objects;

@Controller
public class ChatController {
    // Inject the neural network service
    @Autowired
    private NeuralNetworkService neuralNetworkService;

    // Inject the dataset service
    @Autowired
    private DatasetService datasetService;

    // Inject the process response service
    @Autowired
    private ProcessResponse processResponse;

    // Inject the stemmer utility
    @Autowired
    private Stemmer stemmer;

    // Inject the execute GA service
    @Autowired
    private ExecuteGA executeGA;

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

//        // Get parameter map with user settings
//        Map<String, String[]> parameterMap = new HashMap<String, String[]>();
//        String[] selection = {"untrained"};
//        parameterMap.put("selection", selection);
//
//        // Create an MLP neural network based on user settings
//        MultiLayerPerceptron mlp = createMlp(parameterMap);
//
//        // Create training dataset using the service
//        DataSet trainingSet = datasetService.getTrainingDataset();
//
//        // Train the neural network using the service
//        mlp = neuralNetworkService.trainMultiLayerPerceptron(mlp, trainingSet);
//
//        // Save trained neural network on file
//        mlp.save("./mlp.nnet");
//
//        // Load trained neural network from file
//        NeuralNetwork nnet = NeuralNetwork.createFromFile("./mlp.nnet");
//
//        // Create test dataset using the service
//        DataSet testSet = datasetService.getTestDataset();
//
//        // Test the neural network on the test dataset and get overall probability and correct predictions map
//        Map<String, String> resultsMap = neuralNetworkService.testNeuralNetwork(nnet, testSet);

        // Send message to client
        messagingTemplate.convertAndSendToUser(username, "/chat", data);
    }

    @MessageMapping("/chat")
    public void chat(Message<Object> message, @Payload String payload, Principal principal) throws Exception {
        String username = principal.getName();
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

    /////////////////////////////////////////////////////////////////////////////////
    //                                                                             //
    // HELPER METHODS(2): Create an MLP neural network based on user settings,     //
    // update the http session with new ner tags based on new user input messages. //
    //                                                                             //
    /////////////////////////////////////////////////////////////////////////////////

    // Method to create an MLP neural network based on user settings
    public MultiLayerPerceptron createMlp(Map<String, String[]> parameterMap) {

        MultiLayerPerceptron mlp = null;

        // Get selected mode from the parameter map
        String selectedMode = parameterMap.get("selection")[0];

        if (selectedMode.equals("untrained")) {

            // If mode is untrained, set mode and iterations

            neuralNetworkService.setMode(selectedMode);
            neuralNetworkService.setIterations(0);

        } else if (selectedMode.equals("mlp-01layer")) {

            // Get layer1 size and iterations from parameter map

            String layer1Size = parameterMap.get("mlp-01layer-layer1")[0];
            String iterations = parameterMap.get("mlp-01layer-iterations")[0];

            // If mode is mlp-01layer, set mode, layer1 size and iterations

            neuralNetworkService.setMode(selectedMode);
            neuralNetworkService.setLayer1Size(Integer.parseInt(layer1Size));
            neuralNetworkService.setIterations(Integer.parseInt(iterations));


        } else if (selectedMode.equals("mlp-02layer")) {

            // Get layer1, layer2 sizes and iterations from parameter map

            String layer1Size = parameterMap.get("mlp-02layer-layer1")[0];
            String layer2Size = parameterMap.get("mlp-02layer-layer2")[0];
            String iterations = parameterMap.get("mlp-02layer-iterations")[0];

            // If mode is mlp-02layer, set mode, layer1, layer2 sizes and iterations

            neuralNetworkService.setMode(selectedMode);
            neuralNetworkService.setLayer1Size(Integer.parseInt(layer1Size));
            neuralNetworkService.setLayer2Size(Integer.parseInt(layer2Size));
            neuralNetworkService.setIterations(Integer.parseInt(iterations));


        } else if (selectedMode.equals("mlp-03layer")) {

            // Get layer1, layer2, layer3 sizes and iterations from parameter map

            String layer1Size = parameterMap.get("mlp-03layer-layer1")[0];
            String layer2Size = parameterMap.get("mlp-03layer-layer2")[0];
            String layer3Size = parameterMap.get("mlp-03layer-layer3")[0];
            String iterations = parameterMap.get("mlp-03layer-iterations")[0];

            // If mode is mlp-03layer, set mode, layer1, layer2, layer3 sizes and iterations

            neuralNetworkService.setMode(selectedMode);
            neuralNetworkService.setLayer1Size(Integer.parseInt(layer1Size));
            neuralNetworkService.setLayer2Size(Integer.parseInt(layer2Size));
            neuralNetworkService.setLayer3Size(Integer.parseInt(layer3Size));
            neuralNetworkService.setIterations(Integer.parseInt(iterations));
        }

        // Create the MLP neural network using the service
        mlp = neuralNetworkService.createMultiLayerPerceptron();

        return mlp;
    }
}
