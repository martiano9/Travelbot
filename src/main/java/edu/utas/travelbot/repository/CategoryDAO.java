package edu.utas.travelbot.repository;

import opennlp.tools.doccat.*;
import opennlp.tools.util.*;
import opennlp.tools.util.model.ModelUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryDAO {
    DoccatModel doccat = null;
    public Map<String, String> questionAnswer = new HashMap<>();

    public CategoryDAO() {
        questionAnswer.put("greeting", "Hello, how can I help you?");
        questionAnswer.put("conversation-continue", "What else can I help you with?");
        questionAnswer.put("conversation-complete", "Nice chatting with you. Bye.");
        questionAnswer.put("restaurant-search", "I'm picking the best from the list for you. Visit these wonderful restaurants around your area");
        questionAnswer.put("restaurant-price", "Can you give me the range of your budget? \n<br/>" +
                "\t1. $\n<br/>" +
                "\t2. $$\n<br/>" +
                "\t3. $$$\n<br/>");
        questionAnswer.put("restaurant-from", "Are you going to the restaurant from home?");
        questionAnswer.put("restaurant-waiting", "What is your maximum waiting time?\n<br />" +
                "\t1. 0-10 mins\n<br/>" +
                "\t2. 10-30 mins\n<br/>" +
                "\t3. 30-60 mins\n<br/>" +
                "\t4. >60 mins\n<br/>");
        questionAnswer.put("restaurant-type", "What is your favorite cuisine? Chinese, Japanese, Vietnamese, Malaysian or Thai?");
        questionAnswer.put("restaurant-satisfy", "Are you satisfied with my suggestion?");
        questionAnswer.put("restaurant-more", "Look like you are happy with the result set. If you need further assistant, you can ask me the direction to one of the suggested restaurants or you can ask me to show you more details.");
        questionAnswer.put("take-feedback", "Can you give us feedback about our service, so we can keep improving in the future? Are you satisfied with my service?");
        questionAnswer.put("none", "I'm sorry, I can't find the answer.");

        try {
            InputStreamFactory inputStreamFactory = new MarkableFileInputStreamFactory(new File("categorizer.txt"));
            ObjectStream<String> lineStream = new PlainTextByLineStream(inputStreamFactory, StandardCharsets.UTF_8);

            ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream);

            DoccatFactory factory = new DoccatFactory(); // new FeatureGenerator[] { new BagOfWordsFeatureGenerator() }

            TrainingParameters params = ModelUtil.createDefaultTrainingParameters();
            params.put(TrainingParameters.CUTOFF_PARAM, 0);

            // Train a model with classifications from above file.
            doccat = DocumentCategorizerME.train("en", sampleStream, params, factory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Detect category using given token. Use categorizer feature of Apache OpenNLP.
     *
     * @param finalTokens
     * @return
     * @throws IOException
     */
    public String detectCategory(List<String> finalTokens) {
        String[] arr = new String[finalTokens.size()];
        arr = finalTokens.toArray(arr);
        // Initialize document categorizer tool
        DocumentCategorizerME myCategorizer = new DocumentCategorizerME(doccat);

        // Get best possible category.
        double[] probabilitiesOfOutcomes = myCategorizer.categorize(arr);
        String category = myCategorizer.getBestCategory(probabilitiesOfOutcomes);
        System.out.println("Category: " + category);
        double max = Arrays.stream(probabilitiesOfOutcomes).max().getAsDouble();
        System.out.println("Probabilities: " + max);
        if (max <= 0.2) return "none";
        return category;
    }

    public String getAnswer(String category) {
        return questionAnswer.get(category);
    }
}
